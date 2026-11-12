package coursier.cache

import coursier.cache.TestUtil._
import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import utest._

import java.io.{ByteArrayOutputStream, File}
import java.nio.channels.FileChannel
import java.nio.file.{Files, StandardCopyOption, StandardOpenOption}
import java.util.zip.GZIPOutputStream

import scala.concurrent.duration.DurationInt

/** Responses sent with `Content-Encoding: gzip`, which the cache decodes before writing them.
  *
  * Coursier never sends `Accept-Encoding`, but some servers send gzip anyway, like S3 or GCS for
  * objects stored with that encoding. The lengths and byte ranges of such responses are about the
  * encoded body, while what lands in cache is the decoded one.
  */
object FileCacheGzipTests extends TestSuite {

  // random, so that it doesn't compress much, and the truncated response below has enough of it
  // to decode a few bytes into the .part file
  private val body: Array[Byte] = {
    val b = new Array[Byte](16 * 1024)
    new scala.util.Random(42).nextBytes(b)
    b
  }

  private val gzipped: Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val gzos = new GZIPOutputStream(baos)
    gzos.write(body)
    gzos.close()
    baos.toByteArray
  }

  private def gzipEncoded(resp: RawHttpServer.Response): RawHttpServer.Response =
    resp.copy(headers = resp.headers :+ ("Content-Encoding" -> "gzip"))

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing),
      // much smaller than the body: the bytes decoded before the response is cut short are lost if
      // they don't fill a buffer, and the .part file then stays empty
      bufferSize = 1024
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  val tests = Tests {

    test("resuming a gzip-encoded download starts over") {

      // A range of a gzip-encoded response is a range of the encoded body (RFC 9110 §14.1.2), but
      // the .part file holds decoded bytes: its length isn't an offset in the encoded body, and
      // what comes back doesn't start with a gzip header. Resuming used to fail with
      // "Not in GZIP format", and so did every later attempt, as they all resumed from the same
      // .part file.

      val log = new RequestLog

      @volatile var truncate = true

      RawHttpServer.withServer(log) { req =>
        if (truncate)
          gzipEncoded(RawHttpServer.truncatedChunked(gzipped, gzipped.length / 2))
        else
          // like S3 does
          RawHttpServer.rangeStart(req) match {
            case Some(from) => gzipEncoded(RawHttpServer.partialContent(gzipped, from))
            case None       => gzipEncoded(RawHttpServer.ok(gzipped))
          }
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          assert(run(cache, url).isLeft)
          val partLen = os.size(os.Path(CachePath.temporaryFile(cache.localFile(url))))
          assert(partLen > 0L)

          truncate = false
          log.reset()

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          // the resumed request, then a new one from scratch
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.range.contains(s"bytes=$partLen-"))
          assert(log.entries(1).range.isEmpty)

          assert(java.util.Arrays.equals(os.read.bytes(os.Path(f)), body))

          val dirContent = os.list(os.Path(f) / os.up).map(_.last).sorted
          assert(dirContent == Seq(".foo.jar.checked", "foo.jar"))
        }
      }
    }

    test("watching a gzip-encoded download") {

      // The cache watching a download owned by someone else gets the length to expect with a HEAD
      // request, then compares it to the length of the downloaded file. The Content-Length of a
      // gzip-encoded response is the encoded one, which used to fail with a WrongLength error.

      val log = new RequestLog

      RawHttpServer.withServer(log) { _ =>
        gzipEncoded(RawHttpServer.ok(gzipped))
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          val file = cache.localFile(url)
          os.makeDir.all(os.Path(file.getParentFile))

          // a partial download, as the owner of the lock would have left it
          val part = CachePath.temporaryFile(file)
          os.write(os.Path(part), body.take(8))

          val lockFile = CachePath.lockFile(file)
          val channel = FileChannel.open(
            lockFile.toPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
          )
          val lock = channel.tryLock()
          assert(lock != null)

          val owner = new Thread("lock-owner") {
            override def run(): Unit = {
              // wait for the cache to ask for the length of the download it watches
              val deadline = System.nanoTime() + 30.seconds.toNanos
              while (log.count("HEAD") == 0 && System.nanoTime() < deadline)
                Thread.sleep(20L)
              // the owner completes its download, decoded…
              val tmp = new File(file.getParentFile, "owner-tmp")
              Files.write(tmp.toPath, body)
              Files.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)
              // the watcher finding the .part file empty and the file in cache in the same check
              // doesn't compare lengths, so make sure it gets to see the file on its own first
              Thread.sleep(200L)
              Files.deleteIfExists(part.toPath)
              // … and releases the lock
              lock.release()
              channel.close()
              Files.deleteIfExists(lockFile.toPath)
            }
          }
          owner.setDaemon(true)
          owner.start()

          val res = run(cache, url)
          owner.join()

          val f = res.fold(e => throw new Exception(e.describe), identity)
          assert(java.util.Arrays.equals(os.read.bytes(os.Path(f)), body))

          assert(log.count("GET") == 0)
          assert(log.count("HEAD") > 0)
        }
      }
    }
  }
}
