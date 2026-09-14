package coursier.cache

import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import utest._

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.collection.mutable.ListBuffer

/** The `COURSIER_HTTP_DEBUG` output: which requests go out, what comes back, and which credentials
  * were picked.
  *
  * This output exists for people debugging an authentication setup they cannot otherwise observe (a
  * locked-down proxy, a native `cs` binary with no JVM logging), so the tests pin the exact lines:
  * their wording is the feature. Passwords must never show up in them.
  */
object CacheUrlHttpDebugTests extends TestSuite {

  private val body     = "hello".getBytes(StandardCharsets.UTF_8)
  private val realm    = "test realm"
  private val user     = "alice"
  private val password = "s3cret-pass"

  private val expectedAuthorization = {
    val encoded = Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )
    s"Basic $encoded"
  }

  private def unauthorized: RawHttpServer.Response =
    RawHttpServer.Response(
      "HTTP/1.1 401 Unauthorized",
      Seq(
        "WWW-Authenticate" -> s"""Basic realm="$realm"""",
        "Content-Length"   -> "0"
      )
    )

  private def protectedHandler(entry: RequestLog.Entry): RawHttpServer.Response =
    if (entry.header("Authorization").contains(expectedAuthorization)) RawHttpServer.ok(body)
    else unauthorized

  /** Connects to `url` the way the cache does, returning the final status and the debug lines */
  private def connect(
    url: String,
    credentials: Seq[DirectCredentials],
    authentication: Option[Authentication] = None
  ): (Int, List[String]) = {
    val lines = ListBuffer.empty[String]
    val args = CacheUrl.Args(
      initialUrl = url,
      url0 = url,
      authentication = authentication,
      alreadyDownloaded = 0L,
      followHttpToHttpsRedirections = false,
      followHttpsToHttpRedirections = false,
      autoCredentials = credentials,
      sslSocketFactoryOpt = None,
      hostnameVerifierOpt = None,
      proxyOpt = None,
      method = "GET",
      authRealm = None,
      redirectionCount = 0,
      maxRedirectionsOpt = Some(20),
      classLoaders = Nil,
      httpDebugOpt = Some(line => lines += line)
    )
    val (conn, _) = CacheUrl.urlConnectionMaybePartial(args)
    try {
      val code = conn.asInstanceOf[HttpURLConnection].getResponseCode
      (code, lines.toList)
    }
    finally CacheUrl.closeConn(conn)
  }

  private def withProtectedServer[T](f: (String, RequestLog) => T): T = {
    val log = new RequestLog
    RawHttpServer.withServer(log)(protectedHandler) { baseUrl =>
      f(s"$baseUrl/dir/foo.txt", log)
    }
  }

  private val challenge = s"""(WWW-Authenticate: Basic realm="$realm")"""

  val tests = Tests {

    test("credentials from the environment are sent after the challenge") {
      withProtectedServer { (url, log) =>
        val (code, lines) = connect(url, Seq(DirectCredentials("localhost", user, password)))
        assert(code == 200)
        val expected = List(
          s"GET $url (no credentials)",
          s"HTTP 401 for $url $challenge",
          s"retrying $url with credentials for user $user",
          s"GET $url (credentials for user $user)",
          s"HTTP 200 for $url"
        )
        assert(lines == expected)
        assert(!lines.exists(_.contains(password)))
        // one anonymous request, then the authenticated one
        assert(log.count("GET") == 2)
      }
    }

    test("explicit credentials are sent right away") {
      withProtectedServer { (url, log) =>
        val auth          = Authentication(user, password).copy(httpsOnly = false)
        val (code, lines) = connect(url, Nil, Some(auth))
        assert(code == 200)
        val expected = List(
          s"GET $url (credentials for user $user)",
          s"HTTP 200 for $url"
        )
        assert(lines == expected)
        assert(log.count("GET") == 1)
      }
    }

    test("says which credentials are configured when none match") {
      test("other host") {
        withProtectedServer { (url, _) =>
          val (code, lines) = connect(url, Seq(DirectCredentials("example.com", user, password)))
          assert(code == 401)
          val expected = List(
            s"GET $url (no credentials)",
            s"HTTP 401 for $url $challenge",
            s"""$url: no credentials match host localhost (realm "$realm"), """ +
              "credentials are configured for: example.com, giving up"
          )
          assert(lines == expected)
        }
      }

      test("other realm") {
        withProtectedServer { (url, _) =>
          val credentials = DirectCredentials("localhost", user, password)
            .copy(realm = Some("other realm"))
          val (code, lines) = connect(url, Seq(credentials))
          assert(code == 401)
          assert(lines.last.contains("credentials are configured for: localhost(other realm)"))
        }
      }

      test("nothing configured") {
        withProtectedServer { (url, _) =>
          val (code, lines) = connect(url, Nil)
          assert(code == 401)
          assert(lines.last.endsWith("no credentials are configured, giving up"))
        }
      }
    }

    test("rejected credentials") {
      withProtectedServer { (url, _) =>
        val auth          = Authentication(user, "wrong").copy(httpsOnly = false)
        val (code, lines) = connect(url, Nil, Some(auth))
        assert(code == 401)
        val expected = List(
          s"GET $url (credentials for user $user)",
          s"HTTP 401 for $url $challenge",
          s"retrying $url now that the realm is known",
          s"GET $url (credentials for user $user)",
          s"HTTP 401 for $url $challenge",
          s"$url: credentials for user $user rejected, giving up"
        )
        assert(lines == expected)
        assert(!lines.exists(_.contains("wrong")))
      }
    }
  }
}
