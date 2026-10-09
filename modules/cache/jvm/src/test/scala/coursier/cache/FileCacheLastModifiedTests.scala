package coursier.cache

import coursier.cache.TestUtil._
import coursier.cache.internal.Downloader
import coursier.util.{Artifact, Task}
import utest._

/** How the `Last-Modified` header of a response ends up on the cached file.
  *
  * `HttpURLConnection` only understands some of the ways servers write that header - JitPack used
  * to send `Wed, 09 Jan 2019 18:50:09 Z`, which it gives up on (#1806). Cached files then get the
  * time they were downloaded at, and update checks download them again every time.
  */
object FileCacheLastModifiedTests extends TestSuite {

  private val body: Array[Byte] = "foo".getBytes("UTF-8")

  // Wed, 09 Jan 2019 18:50:09 GMT
  private val expected = 1547059809000L

  private def testCache(dir: os.Path, policy: CachePolicy): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Seq(None),
      cachePolicies = Seq(policy),
      // check for updates every time
      ttl = None
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  private def check(lastModified: String): Unit = {

    val log = new RequestLog
    val response = {
      val ok = RawHttpServer.ok(body)
      ok.copy(headers = ok.headers :+ ("Last-Modified" -> lastModified))
    }

    RawHttpServer.withServer(log)(_ => response) { baseUrl =>
      withTmpDir { dir =>
        val url = s"$baseUrl/dir/foo.txt"

        val res  = run(testCache(dir, CachePolicy.FetchMissing), url)
        val file = res.fold(e => throw new Exception(e), identity)
        assert(file.lastModified() == expected)

        val res0 = run(testCache(dir, CachePolicy.Update), url)
        assert(res0.isRight)
        assert(file.lastModified() == expected)
      }
    }

    // the update check finds the file up-to-date, and doesn't download it again
    val methods = log.methods
    assert(methods == List("GET", "HEAD"))
  }

  val tests = Tests {

    test("GMT") {
      check("Wed, 09 Jan 2019 18:50:09 GMT")
    }

    test("Z") {
      check("Wed, 09 Jan 2019 18:50:09 Z")
    }

    test("UTC") {
      check("Wed, 09 Jan 2019 18:50:09 UTC")
    }

    test("parseHttpDate") {
      val values = Seq(
        "Wed, 09 Jan 2019 18:50:09 GMT",
        "Wed, 09 Jan 2019 18:50:09 Z",
        "Wed, 09 Jan 2019 18:50:09 z",
        "Wed, 09 Jan 2019 18:50:09 UTC",
        "Wed, 09 Jan 2019 18:50:09 UT",
        "Wed, 09 Jan 2019 18:50:09 +0000",
        "Wed, 09 Jan 2019 19:50:09 +0100",
        " Wed, 9 Jan 2019 18:50:09 Z "
      )
      val failures = values
        .map(value => value -> Downloader.parseHttpDate(value))
        .filter(_._2 != Some(expected))
      assert(failures.isEmpty)

      assert(Downloader.parseHttpDate("not a date").isEmpty)
      assert(Downloader.parseHttpDate("Wed, 09 Jan 2019 18:50:09").isEmpty)
    }
  }
}
