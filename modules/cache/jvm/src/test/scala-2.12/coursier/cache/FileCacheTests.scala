package coursier.cache

import java.security.KeyStore
import java.security.cert.X509Certificate

import cats.data.NonEmptyList
import cats.effect.IO
import coursier.cache.TestUtil.withTmpDir
import coursier.core.Artifact
import coursier.util.{Sync, Task}
import javax.net.ssl.{HostnameVerifier, KeyManagerFactory, SSLContext, SSLSession, TrustManager, X509TrustManager}
import org.http4s.dsl.io._
import org.http4s.headers.{Authorization, Location, `WWW-Authenticate`}
import org.http4s.{BasicCredentials, Challenge, HttpService, Request, Uri}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.CaseInsensitiveString
import utest._

import scala.concurrent.ExecutionContext

object FileCacheTests extends TestSuite {

  private val pool = Sync.fixedThreadPool(4)
  private implicit val ec = ExecutionContext.fromExecutorService(pool)

  private def routes(
    redirectBase: => Uri,
    realm: String,
    userPass: (String, String),
    suffix: String = ""
  ): HttpService[IO] = {

    def authorized(req: Request[IO]): Boolean = {

      val res = for {
        token <- req.headers.get(Authorization).map(_.credentials).collect {
          case t: org.http4s.Credentials.Token => t
        }
        if token.authScheme == CaseInsensitiveString("Basic")
        c = BasicCredentials(token.token)
        if (c.username, c.password) == userPass
      } yield ()

      res.nonEmpty
    }

    val unauth =
      Unauthorized(`WWW-Authenticate`(NonEmptyList.one(Challenge("Basic", realm))))

    HttpService[IO] {

      case GET -> Root / "hello" =>
        Ok(s"hello$suffix")

      case GET -> Root / "self-redirect" =>
        TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))

      case GET -> Root / "self-auth-redirect" =>
        TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))

      case GET -> Root / "redirect" =>
        TemporaryRedirect("redirecting", Location(redirectBase / "hello"))

      case GET -> Root / "auth-redirect" =>
        TemporaryRedirect("redirecting", Location(redirectBase / "auth" / "hello"))

      case req @ GET -> Root / "auth" / "hello" =>
        if (authorized(req))
          Ok(s"hello auth$suffix")
        else
          unauth

      case req @ GET -> Root / "auth" / "self-redirect" =>
        if (authorized(req))
          TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))
        else
          unauth

      case req @ GET -> Root / "auth" / "redirect" =>
        if (authorized(req))
          TemporaryRedirect("redirecting", Location(redirectBase / "hello"))
        else
          unauth

      case req @ GET -> Root / "auth" / "auth-redirect" =>
        if (authorized(req))
          TemporaryRedirect("redirecting", Location(redirectBase / "auth" / "hello"))
        else
          unauth
    }
  }

  private val httpRealm = "simple realm"
  private val httpsRealm = "secure realm"
  private val httpUserPass = ("simple", "SiMpLe")
  private val httpsUserPass = ("secure", "sEcUrE")

  private val httpRoutes = routes(
    httpsBaseUri,
    httpRealm,
    httpUserPass
  )
  private val httpsRoutes = routes(
    httpBaseUri,
    httpsRealm,
    httpsUserPass,
    " secure"
  )

  private val clientSslContext: SSLContext = {

    // see https://stackoverflow.com/a/42807185/3714539

    val dummyTrustManager = Array[TrustManager](
      new X509TrustManager {
        def getAcceptedIssuers = null
        def checkClientTrusted(certs: Array[X509Certificate], authType: String) = {}
        def checkServerTrusted(certs: Array[X509Certificate], authType: String) = {}
      }
    )

    val sc = SSLContext.getInstance("SSL")
    sc.init(null, dummyTrustManager, new java.security.SecureRandom)
    sc
  }

  private val serverSslContext: SSLContext = {

    val keyPassword = "ssl-pass"
    val keyManagerPassword = keyPassword

    val ks = KeyStore.getInstance("JKS")
    val ksIs = Thread.currentThread().getContextClassLoader.getResourceAsStream("server.keystore")
    ks.load(ksIs, keyPassword.toCharArray)
    ksIs.close()

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, keyManagerPassword.toCharArray)

    val sc = SSLContext.getInstance("TLS")
    sc.init(kmf.getKeyManagers, null, null)
    sc
  }

  private val httpServer = BlazeBuilder[IO]
    .mountService(httpRoutes)
    .bindLocal(0)
    .start
    .unsafeRunSync()

  assert(httpServer.baseUri.renderString.startsWith("http://"))

  private val httpsServer = BlazeBuilder[IO]
    .mountService(httpsRoutes)
    .withSSLContext(serverSslContext)
    .bindHttp(0, "localhost")
    .start
    .unsafeRunSync()

  private def httpBaseUri: Uri = httpServer.baseUri
  private def httpsBaseUri: Uri = httpsServer.baseUri

  assert(httpsBaseUri.renderString.startsWith("https://"))

  override def utestAfterAll() = {
    httpServer.shutdownNow()
    httpsServer.shutdownNow()
    ec.shutdown()
    pool.shutdown()
  }

  private implicit def artifact(uri: Uri): Artifact =
    Artifact(uri.renderString, Map(), Map(), changing = false, optional = false, None)

  private val dummyHostnameVerifier =
    new HostnameVerifier {
      def verify(s: String, sslSession: SSLSession) = true
    }

  private def fileCache0() = FileCache()
    .withSslSocketFactory(clientSslContext.getSocketFactory)
    .withHostnameVerifier(dummyHostnameVerifier)

  private def expect(uri: Uri, content: String, transform: FileCache[Task] => FileCache[Task] = c => c): Unit =
    withTmpDir { dir =>
      val c = fileCache0()
        .withLocation(dir.toFile)
      val res = transform(c).fetch(uri).run.unsafeRun()
      val expectedRes = Right(content)
      assert(res == expectedRes)
    }

  private def error(uri: Uri, check: String => Boolean, transform: FileCache[Task] => FileCache[Task] = c => c): Unit =
    withTmpDir { dir =>
      val c = fileCache0()
        .withLocation(dir.toFile)
      val res = transform(c).fetch(uri).run.unsafeRun()
      assert(res.isLeft)
      assert(res.left.exists(check))
    }

  private val httpCredentials = Credentials(httpRealm, httpBaseUri.host.fold("")(_.value), httpUserPass._1, httpUserPass._2)
  private val httpsCredentials = Credentials(httpsRealm, httpsBaseUri.host.fold("")(_.value), httpsUserPass._1, httpsUserPass._2)

  val tests = Tests {

    'test - withTmpDir { dir =>
      val c = fileCache0()
        .withLocation(dir.toFile)
      val res = c.fetch(httpsBaseUri / "hello").run.unsafeRun()
      val expectedRes = Right("hello secure")
      assert(res == expectedRes)
    }

    'redirections - {

      'httpToHttp - {
        expect(httpBaseUri / "self-redirect", "hello")
      }

      'httpsToHttps - {
        expect(httpsBaseUri / "self-redirect", "hello secure")
      }

      'httpToHttps - {
        'enabled - {
          expect(
            httpBaseUri / "redirect",
            "hello secure",
            _.withFollowHttpToHttpsRedirections(true)
          )
        }

        'disabled - {
          expect(
            httpBaseUri / "redirect",
            "redirecting"
          )
        }
      }

      'httpToAuthHttps - {
        'enabled - {
          expect(
            httpBaseUri / "auth-redirect",
            "hello auth secure",
            _.withFollowHttpToHttpsRedirections(true)
              .addCredentials(httpsCredentials)
          )
        }

        'disabled - {
          expect(
            httpBaseUri / "auth-redirect",
            "redirecting"
          )
        }
      }

      'httpToAuthHttp - {
        'enabled - {
          expect(
            httpBaseUri / "self-auth-redirect",
            "hello auth",
            _.addCredentials(httpCredentials)
          )
        }

        'enabledAllRealms - {
          expect(
            httpBaseUri / "self-auth-redirect",
            "hello auth",
            _.addCredentials(httpCredentials.withRealm(None))
          )
        }

        'disabled - {
          error(
            httpBaseUri / "self-auth-redirect",
            _.startsWith("unauthorized: ")
          )
        }
      }

      'authHttpToAuthHttp - {
        'enabled - {
          expect(
            httpBaseUri / "auth" / "self-redirect",
            "hello auth",
            _.addCredentials(httpCredentials)
          )
        }

        'enabledAllRealms - {
          expect(
            httpBaseUri / "auth" / "self-redirect",
            "hello auth",
            _.addCredentials(httpCredentials.withRealm(None))
          )
        }

        'disabled - {
          error(
            httpBaseUri / "auth" / "self-redirect",
            _.startsWith("unauthorized: ")
          )
        }
      }

      'httpsToAuthHttps - {
        'enabled - {
          expect(
            httpsBaseUri / "self-auth-redirect",
            "hello auth secure",
            _.addCredentials(httpsCredentials)
          )
        }

        'enabledAllRealms - {
          expect(
            httpsBaseUri / "self-auth-redirect",
            "hello auth secure",
            _.addCredentials(httpsCredentials.withRealm(None))
          )
        }

        'disabled - {
          error(
            httpsBaseUri / "self-auth-redirect",
            _.startsWith("unauthorized: ")
          )
        }
      }

      'authHttpsToAuthHttps - {
        'enabled - {
          expect(
            httpsBaseUri / "auth" / "self-redirect",
            "hello auth secure",
            _.addCredentials(httpsCredentials)
          )
        }

        'enabledAllRealms - {
          expect(
            httpsBaseUri / "auth" / "self-redirect",
            "hello auth secure",
            _.addCredentials(httpsCredentials.withRealm(None))
          )
        }

        'disabled - {
          error(
            httpsBaseUri / "auth" / "self-redirect",
            _.startsWith("unauthorized: ")
          )
        }
      }

    }
  }

}
