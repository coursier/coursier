package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

import java.io.File

/** Behavioural baseline for partial downloads and resuming.
  *
  * Nothing here was covered before: neither `ConnectionBuilder.connectionMaybePartial()` nor the
  * append branch of `Downloader.Blocking.doDownload`. Content-only assertions are especially
  * useless here - a resume that silently refetches the whole artifact produces a byte-perfect file.
  */
object FileCacheResumeTests extends TestSuite {

  private val body: Array[Byte] =
    (0 until 512).map(i => ('a' + (i % 26)).toChar).mkString.getBytes("UTF-8")

  /** How much of the body the server hands over before hanging up. */
  private val truncateAt = 40

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing)
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  /** The whole directory the artifact lives in, as (name, size) pairs. */
  private def layout(cache: FileCache[Task], url: String): Seq[(String, Long)] = {
    val dir = os.Path(cache.localFile(url).getParentFile)
    if (os.exists(dir))
      os.list(dir).filter(os.isFile).map(p => (p.last, os.size(p))).sortBy(_._1)
    else
      Nil
  }

  private def partFile(cache: FileCache[Task], url: String): File =
    coursier.paths.CachePath.temporaryFile(cache.localFile(url))

  val tests = Tests {

    test("a truncated download leaves a .part file behind") {

      val log = new RequestLog

      RawHttpServer.withServer(log) { _ =>
        RawHttpServer.truncatedChunked(body, truncateAt)
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          val res = run(cache, url)

          res match {
            case Left(_: ArtifactError.DownloadError) =>
            case other => sys.error(s"Expected a download error, got $other")
          }
          assert(log.methods == List("GET"))

          // the bytes that did make it are kept, for the next attempt to resume from
          assert(layout(cache, url) == Seq((".foo.jar.part", truncateAt.toLong)))
        }
      }
    }

    test("resumes from the .part file") {

      val log = new RequestLog

      // first attempt is cut short, later ones honour the Range header
      @volatile var truncate = true

      RawHttpServer.withServer(log) { req =>
        if (truncate) RawHttpServer.truncatedChunked(body, truncateAt)
        else
          RawHttpServer.rangeStart(req) match {
            case Some(from) => RawHttpServer.partialContent(body, from)
            case None       => RawHttpServer.ok(body)
          }
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          assert(run(cache, url).isLeft)
          assert(os.size(os.Path(partFile(cache, url))) == truncateAt.toLong)

          truncate = false
          log.reset()

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          // the retry asked for the missing bytes only…
          assert(log.methods == List("GET"))
          assert(log.entries.head.range.contains(s"bytes=$truncateAt-"))

          // … and what landed in cache is the whole artifact, byte for byte
          val downloaded = os.read.bytes(os.Path(f))
          assert(java.util.Arrays.equals(downloaded, body))

          // no .part and no .length side file left over
          assert(layout(cache, url) == Seq(
            (".foo.jar.checked", 0L),
            ("foo.jar", body.length.toLong)
          ))
        }
      }
    }

    test("the .length side file is cleaned up") {

      // .length is only written when the response announces a Content-Length, which the 206 of a
      // resumed download does
      val log = new RequestLog

      @volatile var truncate = true

      RawHttpServer.withServer(log) { req =>
        if (truncate) RawHttpServer.truncatedChunked(body, truncateAt)
        else
          RawHttpServer.rangeStart(req) match {
            case Some(from) => RawHttpServer.partialContent(body, from)
            case None       => RawHttpServer.ok(body)
          }
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          assert(run(cache, url).isLeft)
          truncate = false
          assert(run(cache, url).isRight)

          val leftOver = layout(cache, url).map(_._1).filter(_.endsWith(".length"))
          assert(leftOver.isEmpty)
        }
      }
    }

    test("a server that ignores Range") {

      // The client sent `Range: bytes=40-` and got a plain 200 carrying the *whole* body back. That
      // body must replace the 40 bytes already on disk, not be appended to them.

      val log = new RequestLog

      @volatile var truncate = true

      RawHttpServer.withServer(log) { _ =>
        if (truncate) RawHttpServer.truncatedChunked(body, truncateAt)
        else RawHttpServer.ok(body) // Range simply ignored
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          assert(run(cache, url).isLeft)
          truncate = false
          log.reset()

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(log.methods == List("GET"))
          assert(log.entries.head.range.contains(s"bytes=$truncateAt-"))

          val downloaded = os.read.bytes(os.Path(f))
          assert(java.util.Arrays.equals(downloaded, body))
          assert(layout(cache, url) == Seq(
            (".foo.jar.checked", 0L),
            ("foo.jar", body.length.toLong)
          ))
        }
      }
    }

    test("a server that ignores Range overwrites a pre-existing .part file") {

      // bytes that aren't a prefix of the body, so that keeping any of them would show
      val garbage = Array.fill[Byte](truncateAt)('X'.toByte)

      val log = new RequestLog

      RawHttpServer.withServer(log) { _ =>
        RawHttpServer.ok(body) // Range simply ignored
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          os.write(os.Path(partFile(cache, url)), garbage, createFolders = true)

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(log.methods == List("GET"))
          assert(log.entries.head.range.contains(s"bytes=$truncateAt-"))

          val downloaded = os.read.bytes(os.Path(f))
          assert(java.util.Arrays.equals(downloaded, body))
          assert(layout(cache, url) == Seq(
            (".foo.jar.checked", 0L),
            ("foo.jar", body.length.toLong)
          ))
        }
      }
    }

    test("a 206 with a wrong Content-Range starts over") {

      val garbage = Array.fill[Byte](truncateAt)('X'.toByte)

      val log = new RequestLog

      RawHttpServer.withServer(log) { req =>
        RawHttpServer.rangeStart(req) match {
          // a partial response, but not starting at the offset asked for
          case Some(from) => RawHttpServer.partialContent(body, from + 10)
          case None       => RawHttpServer.ok(body)
        }
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          os.write(os.Path(partFile(cache, url)), garbage, createFolders = true)

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          // the mismatching 206 is dropped, and the whole artifact is asked for again
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.range.contains(s"bytes=$truncateAt-"))
          assert(log.entries(1).range.isEmpty)

          val downloaded = os.read.bytes(os.Path(f))
          assert(java.util.Arrays.equals(downloaded, body))
          assert(layout(cache, url) == Seq(
            (".foo.jar.checked", 0L),
            ("foo.jar", body.length.toLong)
          ))
        }
      }
    }

    test("a 416 starts over") {

      // a .part file longer than the artifact on the server, e.g. as the latter changed since
      val garbage = Array.fill[Byte](body.length + 10)('X'.toByte)

      val log = new RequestLog

      RawHttpServer.withServer(log) { req =>
        RawHttpServer.rangeStart(req) match {
          case Some(_) =>
            RawHttpServer.Response(
              "HTTP/1.1 416 Range Not Satisfiable",
              Seq("Content-Length" -> "0", "Content-Range" -> s"bytes */${body.length}")
            )
          case None => RawHttpServer.ok(body)
        }
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          os.write(os.Path(partFile(cache, url)), garbage, createFolders = true)

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.range.contains(s"bytes=${garbage.length}-"))
          assert(log.entries(1).range.isEmpty)

          val downloaded = os.read.bytes(os.Path(f))
          assert(java.util.Arrays.equals(downloaded, body))
        }
      }
    }

    test("a truncated fixed-length response is not detected") {

      // Current behaviour, pinned here as a baseline rather than endorsed. A response announcing
      // `Content-Length: 512` and then hanging up after 40 bytes is reported as a clean end of
      // stream by HttpURLConnection, and nothing downstream compares the bytes read against the
      // announced length - so a truncated artifact is moved into cache and reported as a success.
      // Only a checksum would catch it, and checksums are optional.

      val log = new RequestLog

      RawHttpServer.withServer(log) { _ =>
        RawHttpServer.truncatedFixedLength(body, truncateAt)
      } { baseUrl =>
        withTmpDir { dir =>

          val cache = testCache(dir)
          val url   = s"$baseUrl/dir/foo.jar"

          val res = run(cache, url)
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(os.size(os.Path(f)) == truncateAt.toLong)
          assert(layout(cache, url) == Seq(
            (".foo.jar.checked", 0L),
            ("foo.jar", truncateAt.toLong)
          ))
        }
      }
    }
  }
}
