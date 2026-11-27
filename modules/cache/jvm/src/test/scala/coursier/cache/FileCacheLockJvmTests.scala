package coursier.cache

import coursier.cache.TestUtil._
import coursier.paths.CachePath
import coursier.util.{Artifact, Sync, Task}
import utest._

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel
import java.nio.file.{Files, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.DurationInt

/** The locking tests that cannot run on Scala Native.
  *
  * Both simulate a *second process* holding a lock, from a second thread of this one. That only
  * works on the JVM, which keeps its own registry of the `FileLock`s it has handed out and reports
  * an overlapping request as an `OverlappingFileLockException`. Scala Native's `FileChannel.lock`
  * is a plain `fcntl` record lock, and those are owned by the process - a second request from the
  * same process is granted, so a competing holder in-process is invisible.
  *
  * That is a limitation of the *simulation*, not of the cache: within one process, concurrent
  * fetches of one URL are serialised by `CacheLocks.withUrlLock`, and `CachePath.withStructureLock`
  * takes an intra-process monitor before it goes near the lock file. Both of those are covered by
  * [[FileCacheLockTests]], which does run on Native.
  */
object FileCacheLockJvmTests extends TestSuite {

  private val content = "the artifact content, long enough to be downloaded in several chunks " * 32

  private def handler(
    slowDownload: Boolean = false,
    found: Boolean = true
  )(
    entry: RequestLog.Entry
  ): RawHttpServer.Response =
    if (found && entry.path == "/dir/foo.jar") {
      if (slowDownload && entry.method == "GET")
        Thread.sleep(500L)
      RawHttpServer.ok(content)
    }
    else
      RawHttpServer.notFound

  private def testCache(cacheDir: os.Path): FileCache[Task] =
    FileCache[Task](cacheDir.toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing),
      // a pool of our own, so that the number of threads available doesn't depend on what else
      // runs in this JVM
      pool = Sync.fixedThreadPool(8)
    )

  private def daemonThread(name: String)(f: => Unit): Thread = {
    val t = new Thread(name) {
      override def run(): Unit = f
    }
    t.setDaemon(true)
    t
  }

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

    test("watch a download owned by someone else") {

      // Simulates another process holding the lock on the artifact while downloading it: the lock
      // is taken from outside the cache, and the artifact shows up in cache a little later. The
      // cache under test must watch that download rather than start one of its own.

      val log    = new RequestLog
      val logger = new WatchingLogger

      RawHttpServer.withServer(log)(handler()) { baseUrl =>
        withTmpDir { dir =>

          val cache    = testCache(dir / "cache").copy(logger = logger)
          val url      = s"$baseUrl/dir/foo.jar"
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
            // wait for the cache to watch the download, rather than for a fixed delay that a slow
            // machine can exceed before the cache even gets to the lock (it then finds the
            // artifact in cache, and watches nothing)
            val deadline = System.nanoTime() + 30.seconds.toNanos
            while (!logger.watched.get() && System.nanoTime() < deadline)
              Thread.sleep(20L)
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
