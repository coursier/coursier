package coursier.cache

import coursier.cache.TestUtil._
import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import coursier.util.{Artifact, Task}
import utest._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** `FileCache.withAuthRealm`: assuming a realm before the server has named one.
  *
  * Credentials that name a realm are withheld until the server sends a challenge naming that same
  * realm. That costs an anonymous round trip per artifact, and it only works against servers that
  * do challenge us - the last test here is the case it fails for, an Azure DevOps style feed that
  * answers an unauthenticated request with a sign-in page rather than a 401.
  */
object FileCacheAuthRealmTests extends TestSuite {

  private val realm      = "test realm"
  private val user       = "alice"
  private val password   = "s3cret-pass"
  private val jarContent = "the artifact".getBytes(UTF_8)
  private val signInPage = "<html>please sign in</html>".getBytes(UTF_8)

  private val expectedAuthorization = {
    val encoded = Base64.getEncoder.encodeToString(s"$user:$password".getBytes(UTF_8))
    s"Basic $encoded"
  }

  private def authorized(entry: RequestLog.Entry): Boolean =
    entry.header("Authorization").contains(expectedAuthorization)

  /** Answers an unauthenticated request with a 401 naming the realm, as a well-behaved server does.
    */
  private def challenging(entry: RequestLog.Entry): RawHttpServer.Response =
    if (authorized(entry)) RawHttpServer.ok(jarContent)
    else
      RawHttpServer.Response(
        "HTTP/1.1 401 Unauthorized",
        Seq(
          "WWW-Authenticate" -> s"""Basic realm="$realm"""",
          "Content-Length"   -> "0"
        )
      )

  /** Answers an unauthenticated request with a sign-in page under a 203, as Azure DevOps does. */
  private def signingIn(entry: RequestLog.Entry): RawHttpServer.Response =
    if (authorized(entry)) RawHttpServer.ok(jarContent)
    else
      RawHttpServer.Response(
        "HTTP/1.1 203 Non-Authoritative Information",
        Seq("Content-Length" -> signInPage.length.toString),
        signInPage
      )

  /** Credentials scoped to `realm`, as `credentials.properties` or a repository would carry them */
  private val authentication =
    Authentication(user, password).copy(
      realmOpt = Some(realm),
      httpsOnly = false
    )

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing)
    )

  private def run(
    cache: FileCache[Task],
    url: String,
    artifactAuthentication: Option[Authentication] = Some(authentication)
  ) =
    cache
      .file(Artifact(url).copy(authentication = artifactAuthentication))
      .run
      .unsafeRun(wrapExceptions = true)(cache.ec)

  private def withServer[T](
    handler: RequestLog.Entry => RawHttpServer.Response
  )(
    f: (String, RequestLog) => T
  ): T = {
    val log = new RequestLog
    RawHttpServer.withServer(log)(handler) { baseUrl =>
      f(s"$baseUrl/dir/foo.jar", log)
    }
  }

  private def expectContent(
    cache: FileCache[Task],
    url: String,
    expected: Array[Byte],
    artifactAuthentication: Option[Authentication] = Some(authentication)
  ): Unit = {
    val file = run(cache, url, artifactAuthentication) match {
      case Right(f)  => f
      case Left(err) => sys.error(s"Expected a successful download, got $err")
    }
    assert(os.read.bytes(os.Path(file)).sameElements(expected))
  }

  val tests = Tests {

    test("without a realm, credentials wait for the challenge") {
      withServer(challenging) { (url, log) =>
        withTmpDir { dir =>
          expectContent(testCache(dir), url, jarContent)
          // one anonymous request, then the same one with credentials
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.header("Authorization").isEmpty)
        }
      }
    }

    test("the assumed realm sends them right away") {
      withServer(challenging) { (url, log) =>
        withTmpDir { dir =>
          expectContent(testCache(dir).withAuthRealm(realm), url, jarContent)
          assert(log.methods == List("GET"))
          assert(log.entries.head.header("Authorization").contains(expectedAuthorization))
        }
      }
    }

    test("a realm the server disagrees with falls back to the challenge") {
      withServer(challenging) { (url, log) =>
        withTmpDir { dir =>
          expectContent(testCache(dir).withAuthRealm("some other realm"), url, jarContent)
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.header("Authorization").isEmpty)
        }
      }
    }

    test("credentials from the environment are held back either way") {
      // they are optional, unlike the ones a repository carries: an assumed realm does not make
      // them go out before the server has asked, it only spares them the realm mismatch
      withServer(challenging) { (url, log) =>
        withTmpDir { dir =>
          val cache = testCache(dir)
            .addCredentials(DirectCredentials("localhost", user, password).withRealm(realm))
            .withAuthRealm(realm)
          expectContent(cache, url, jarContent, artifactAuthentication = None)
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.header("Authorization").isEmpty)
        }
      }
    }

    test("a feed that answers 203 instead of challenging") {

      test("caches the sign-in page as it stands") {
        withServer(signingIn) { (url, log) =>
          withTmpDir { dir =>
            // no challenge, so the realm stays unknown and the credentials are never sent
            expectContent(testCache(dir), url, signInPage)
            assert(log.methods == List("GET"))
          }
        }
      }

      test("fails rather than caching it when 203 is rejected") {
        withServer(signingIn) { (url, _) =>
          withTmpDir { dir =>
            val cache = testCache(dir).copy(rejectNonAuthoritativeResponses = true)
            run(cache, url) match {
              case Left(_: ArtifactError.NonAuthoritative) =>
              case other => sys.error(s"Expected a non-authoritative error, got $other")
            }
          }
        }
      }

      test("gets the artifact once the realm is assumed") {
        withServer(signingIn) { (url, log) =>
          withTmpDir { dir =>
            val cache = testCache(dir)
              .withAuthRealm(realm)
              .copy(rejectNonAuthoritativeResponses = true)
            expectContent(cache, url, jarContent)
            assert(log.methods == List("GET"))
            assert(log.entries.head.header("Authorization").contains(expectedAuthorization))
          }
        }
      }
    }
  }
}
