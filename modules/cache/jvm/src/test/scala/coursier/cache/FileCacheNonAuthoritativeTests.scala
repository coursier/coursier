package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

import java.nio.charset.StandardCharsets.UTF_8

/** HTTP 203 responses, and the `rejectNonAuthoritativeResponses` option that turns them into an
  * error.
  *
  * A 203 says the body is not the one the origin server would have sent - something along the way
  * transformed it. That is usually harmless, and coursier keeps such a body, which is what the
  * first test pins.
  *
  * It is not harmless when a repository answers 203 in place of an authentication challenge: Azure
  * DevOps artifact feeds return a sign-in page that way, and it lands in the cache under the
  * artifact's name, where it is indistinguishable from a real download. Rejecting 203 is opt-in for
  * that reason - nobody behind a transforming proxy should start seeing failures.
  */
object FileCacheNonAuthoritativeTests extends TestSuite {

  private val signInPage = "<html>please sign in</html>".getBytes(UTF_8)
  private val jarContent = "the artifact".getBytes(UTF_8)

  private def nonAuthoritative: RawHttpServer.Response =
    RawHttpServer.Response(
      "HTTP/1.1 203 Non-Authoritative Information",
      Seq("Content-Length" -> signInPage.length.toString),
      signInPage
    )

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing)
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  private def withServer[T](
    response: RawHttpServer.Response
  )(
    f: (String, RequestLog) => T
  ): T = {
    val log = new RequestLog
    RawHttpServer.withServer(log)(_ => response) { baseUrl =>
      f(s"$baseUrl/dir/foo.jar", log)
    }
  }

  val tests = Tests {

    test("a 203 body is cached by default") {
      withServer(nonAuthoritative) { (url, log) =>
        withTmpDir { dir =>
          val cache = testCache(dir)
          val file  = run(cache, url) match {
            case Right(f)  => f
            case Left(err) => sys.error(s"Expected the 203 body to be cached, got $err")
          }
          assert(log.methods == List("GET"))
          assert(os.read.bytes(os.Path(file)).sameElements(signInPage))
        }
      }
    }

    test("a 203 is an error when the cache rejects them") {
      withServer(nonAuthoritative) { (url, log) =>
        withTmpDir { dir =>
          val cache = testCache(dir).copy(rejectNonAuthoritativeResponses = true)
          run(cache, url) match {
            case Left(err: ArtifactError.NonAuthoritative) =>
              assert(err.url == url)
              // the response code is what makes this actionable, so it is part of the message
              assert(err.describe.contains("HTTP 203"))
            case other =>
              sys.error(s"Expected a non-authoritative error, got $other")
          }
          // not a retryable error: the request is issued once, and the body is not kept
          assert(log.methods == List("GET"))
          assert(!cache.localFile(url).exists())
        }
      }
    }

    test("rejecting 203 leaves other responses alone") {
      withServer(RawHttpServer.ok(jarContent)) { (url, log) =>
        withTmpDir { dir =>
          val cache = testCache(dir).copy(rejectNonAuthoritativeResponses = true)
          val file  = run(cache, url) match {
            case Right(f)  => f
            case Left(err) => sys.error(s"Expected a successful download, got $err")
          }
          assert(log.methods == List("GET"))
          assert(os.read.bytes(os.Path(file)).sameElements(jarContent))
        }
      }
    }
  }
}
