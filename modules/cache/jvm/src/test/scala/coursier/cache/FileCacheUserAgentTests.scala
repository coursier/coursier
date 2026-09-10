package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

/** The User-Agent coursier sends, and how callers override it.
  *
  * The header is not cosmetic: some repositories serve different content to the "Java/…" agent
  * `HttpURLConnection` sends by default, which is why `CacheUrl` sets one at all. Assert on what
  * reaches the server - the cached bytes are identical either way.
  */
object FileCacheUserAgentTests extends TestSuite {

  private val body: Array[Byte] = "foo".getBytes("UTF-8")

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing)
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  private def userAgentSentBy(cache: os.Path => FileCache[Task]): Option[String] = {

    val log = new RequestLog

    RawHttpServer.withServer(log)(_ => RawHttpServer.ok(body)) { baseUrl =>
      withTmpDir { dir =>
        val url = s"$baseUrl/dir/foo.txt"
        val res = run(cache(dir), url)
        assert(res.isRight)
      }
    }

    val entries = log.entries
    assert(entries.length == 1)
    entries.head.header("User-Agent")
  }

  val tests = Tests {

    test("sends the default user agent") {
      val agent = userAgentSentBy(testCache)
      assert(agent == Some(CacheUrl.defaultUserAgent))
    }

    test("sends the user agent set on the cache") {
      val agent = userAgentSentBy(dir => testCache(dir).withUserAgent("Custom/1.2"))
      assert(agent == Some("Custom/1.2"))
    }
  }
}
