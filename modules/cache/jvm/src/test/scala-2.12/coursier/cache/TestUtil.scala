package coursier.cache

import java.io.File
import java.nio.file.{Files, Path}
import java.security.KeyStore
import java.security.cert.X509Certificate

import cats.data.NonEmptyList
import cats.effect.IO
import coursier.core.{Artifact, Authentication}
import javax.net.ssl.{HostnameVerifier, KeyManagerFactory, SSLContext, SSLSession, TrustManager, X509TrustManager}
import org.http4s.dsl.io._
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.{BasicCredentials, Challenge, HttpService, Request, Uri}

object TestUtil {

  lazy val dummyClientSslContext: SSLContext = {

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

  def dummyHostnameVerifier: HostnameVerifier =
    new HostnameVerifier {
      def verify(s: String, sslSession: SSLSession) = true
    }

  private lazy val serverSslContext: SSLContext = {

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

  def withHttpServer[T](
    routes: HttpService[IO],
    withSsl: Boolean = false
  )(
    f: Uri => T
  ): T = {

    val server = {

      val builder = BlazeBuilder[IO]
        .mountService(routes)

      (if (withSsl) builder.withSSLContext(serverSslContext) else builder)
        .bindHttp(0, "localhost")
        .start
        .unsafeRunSync()
    }

    assert(server.baseUri.renderString.startsWith(if (withSsl) "https://" else "http://"))

    try f(server.baseUri)
    finally {
      server.shutdownNow()
    }
  }

  def authorized(req: Request[IO], userPass: (String, String)): Boolean = {

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

  def unauth(realm: String) =
    Unauthorized(`WWW-Authenticate`(NonEmptyList.one(Challenge("Basic", realm))))

  implicit class UserUriOps(private val uri: Uri) extends AnyVal {
    def withUser(user: String): Uri =
      uri.copy(
        authority = uri.authority.map { authority =>
          authority.copy(
            userInfo = Some(user)
          )
        }
      )
  }

  implicit def artifact(uri: Uri): Artifact = {
    val (uri0, authOpt) = uri.userInfo match {
      case Some(info) =>
        assert(!info.contains(':'))
        (uri.copy(authority = uri.authority.map(_.copy(userInfo = None))), Some(Authentication(info)))
      case None =>
        (uri, None)
    }
    Artifact(uri0.renderString, Map(), Map(), changing = false, optional = false, authOpt)
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-test")
    val shutdownHook: Thread =
      new Thread {
        override def run() =
          deleteRecursive(dir.toFile)
      }
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    }
  }

}
