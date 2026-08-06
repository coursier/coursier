package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.paths.CachePath
import coursier.util.{Artifact, Sync, Task}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import utest._

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel
import java.nio.file.{Files, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.DurationInt

/** Behavioural baseline for the locking done around downloads.
  *
  * These assert on the requests the server receives, not only on the resulting content: a lock
  * released too early, or not taken at all, yields the very same (correct) content, just downloaded
  * several times.
  */
object FileCacheLockTests extends TestSuite {

  private val content = "the artifact content, long enough to be downloaded in several chunks " * 32

  private def routes(
    slowDownload: Boolean = false,
    found: Boolean = true
  ): HttpRoutes[IO] = {
    // HEAD is served too: the watcher path does HEAD requests to get the length of the download it
    // is watching, and a 404 there would hand it a bogus length
    def get =
      if (slowDownload) IO.sleep(500.millis).flatMap(_ => Ok(content))
      else Ok(content)
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.jar" if found  => get
      case HEAD -> Root / "dir" / "foo.jar" if found => Ok(content)
    }
  }

  private def testCache(cacheDir: os.Path): FileCache[Task] =
    FileCache[Task](cacheDir.toIO)
      .withChecksums(Nil)
      .withCachePolicies(Seq(CachePolicy.FetchMissing))
      // a pool of our own, so that the number of threads available doesn't depend on what else
      // runs in this JVM
      .withPool(Sync.fixedThreadPool(8))

  private def daemonThread(name: String)(f: => Unit): Thread = {
    val t = new Thread(name) {
      override def run(): Unit = f
    }
    t.setDaemon(true)
    t
  }

  private def lockOrPartFiles(cacheDir: os.Path): Seq[os.SubPath] =
    if (os.exists(cacheDir))
      os.walk(cacheDir)
        .filter(os.isFile)
        .filter(p => p.last.endsWith(".lock") || p.last.endsWith(".part"))
        .map(_.relativeTo(cacheDir).asSubPath)
    else
      Nil

  /** Records whether the logger was told about a download it was only watching. */
  private final class WatchingLogger extends CacheLogger {
    val watched = new AtomicBoolean(false)
    override def downloadLength(
      url: String,
      totalLength: Long,
      alreadyDownloaded: Long,
      watching: Boolean
    ): Unit =
      if (watching)
        watched.set(true)
  }

  /** Takes the structure lock from outside the cache, and returns a way of releasing it. */
  private def holdStructureLock(cacheDir: os.Path): () => Unit = {
    os.makeDir.all(cacheDir)
    val out      = new FileOutputStream(new File(cacheDir.toIO, ".structure.lock"))
    val lock     = out.getChannel.lock()
    val released = new AtomicBoolean(false)
    () =>
      if (released.compareAndSet(false, true)) {
        lock.release()
        out.close()
      }
  }

  val tests = Tests {

    test("only one download for concurrent requests on the same artifact") {

      val log = new RequestLog

      withHttpServer(logRequests(log)(routes(slowDownload = true))) { serverUri =>
        withTmpDir { dir =>

          val cache    = testCache(dir / "cache")
          val artifact = Artifact((serverUri / "dir" / "foo.jar").renderString)

          val results = runConcurrently(4) { _ =>
            cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)
          }

          for (res <- results) {
            val f = res.fold(e => throw new Exception(e.describe), identity)
            assert(os.read(os.Path(f)) == content)
          }

          // the point of the test: the other callers waited for the download in flight rather
          // than starting one of their own
          val getCount = log.count("GET")
          if (getCount != 1)
            sys.error(s"Expected exactly one GET, got $getCount (requests: ${log.methodsAndPaths})")

          // … and that they did wait, rather than find the artifact already in cache: the HEAD
          // requests are the ones the watcher path does to report the progress of the download
          // it is watching
          assert(log.count("HEAD") > 0)

          assert(lockOrPartFiles(dir / "cache").isEmpty)
        }
      }
    }

    test("watch a download owned by someone else") {

      // Simulates another process holding the lock on the artifact while downloading it: the lock
      // is taken from outside the cache, and the artifact shows up in cache a little later. The
      // cache under test must watch that download rather than start one of its own.

      val log    = new RequestLog
      val logger = new WatchingLogger

      withHttpServer(logRequests(log)(routes())) { serverUri =>
        withTmpDir { dir =>

          val cache    = testCache(dir / "cache").withLogger(logger)
          val url      = (serverUri / "dir" / "foo.jar").renderString
          val artifact = Artifact(url)

          val file = cache.localFile(url)
          os.makeDir.all(os.Path(file.getParentFile))

          // a partial download, as the owner of the lock would have left it
          val part = CachePath.temporaryFile(file)
          os.write(os.Path(part), content.take(8))

          val lockFile = CachePath.lockFile(file)
          val channel = FileChannel.open(
            lockFile.toPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
          )
          val lock = channel.tryLock()
          assert(lock != null)

          val owner = daemonThread("lock-owner") {
            Thread.sleep(300L)
            // the owner completes its download…
            val tmp = new File(file.getParentFile, "owner-tmp")
            Files.write(tmp.toPath, content.getBytes("UTF-8"))
            Files.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)
            Files.deleteIfExists(part.toPath)
            // … and releases the lock
            lock.release()
            channel.close()
            Files.deleteIfExists(lockFile.toPath)
          }
          owner.start()

          val res = cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)
          owner.join()

          val f = res.fold(e => throw new Exception(e.describe), identity)
          assert(os.read(os.Path(f)) == content)

          // no download of our own - only the HEAD requests the watcher path does to report the
          // progress of the download it is watching
          if (log.count("GET") != 0)
            sys.error(s"Expected no GET, got ${log.methodsAndPaths}")
          assert(log.count("HEAD") > 0)
          assert(logger.watched.get())
        }
      }
    }

    test("lock file cleanup") {

      def check(found: Boolean, expectSuccess: Boolean): Unit =
        withHttpServer(routes(found = found)) { serverUri =>
          withTmpDir { dir =>
            val cache = testCache(dir / "cache")
            val res = cache
              .file(Artifact((serverUri / "dir" / "foo.jar").renderString)).run
              .unsafeRun(wrapExceptions = true)(cache.ec)
            assert(res.isRight == expectSuccess)
            val leftOver = lockOrPartFiles(dir / "cache")
            if (leftOver.nonEmpty)
              sys.error(s"Left over lock / part files: $leftOver")
          }
        }

      test("after a successful download") {
        check(found = true, expectSuccess = true)
      }
      test("after a failed download") {
        check(found = false, expectSuccess = false)
      }
    }

    test("structure lock") {

      test("retries while the lock is held") {
        withTmpDir { dir =>
          val cacheDir = dir / "cache"
          val release  = holdStructureLock(cacheDir)
          try {
            val holder = daemonThread("structure-lock-holder") {
              Thread.sleep(20L)
              release()
            }
            holder.start()
            val start     = System.nanoTime()
            val res       = CacheLocks.withStructureLock(cacheDir.toIO)("ok")
            val elapsedMs = (System.nanoTime() - start) / 1000000L
            holder.join()
            assert(res == "ok")
            // the lock was held when the call started, so the first attempt cannot have
            // succeeded - at least one retry delay was waited out
            assert(elapsedMs >= 10L)
          }
          finally release()
        }
      }

      test("gives up after too many attempts") {
        withTmpDir { dir =>
          val cacheDir = dir / "cache"
          val release  = holdStructureLock(cacheDir)
          try {
            val ran = new AtomicBoolean(false)
            val exOpt =
              try {
                CacheLocks.withStructureLock(cacheDir.toIO) {
                  ran.set(true)
                }
                None
              }
              catch {
                case e: Exception => Some(e)
              }
            assert(!ran.get())
            exOpt match {
              case Some(ex) =>
                assert(ex.getClass.getName.contains("StructureLock"))
              case None =>
                sys.error("Expected the structure lock to give up, it succeeded instead")
            }
          }
          finally release()
        }
      }
    }
  }
}
