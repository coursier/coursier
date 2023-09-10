package coursier.cache

import java.io.File
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.security.{KeyStore, MessageDigest}
import java.security.cert.X509Certificate

import cats.data.NonEmptyList
import cats.effect.IO
import coursier.core.Authentication
import coursier.util.Artifact
import javax.net.ssl.{
  HostnameVerifier,
  KeyManagerFactory,
  SSLContext,
  SSLSession,
  TrustManager,
  X509TrustManager
}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.server.Router
import org.http4s.util.CaseInsensitiveString
import org.http4s.{BasicCredentials, Challenge, HttpRoutes, Request, Uri}
import org.typelevel.ci.CIString

import scala.concurrent.ExecutionContext

object TestUtil {

  lazy val dummyClientSslContext: SSLContext = {

    // see https://stackoverflow.com/a/42807185/3714539

    val dummyTrustManager = Array[TrustManager](
      new X509TrustManager {
        def getAcceptedIssuers                                                  = null
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

    val keyPassword        = "ssl-pass"
    val keyManagerPassword = keyPassword

    val ks   = KeyStore.getInstance("JKS")
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
    routes: HttpRoutes[IO],
    withSsl: Boolean = false
  )(
    f: Uri => T
  ): T = {

    import cats.effect.unsafe.implicits.global

    val server = {

      val builder = BlazeServerBuilder[IO]
        .withHttpApp(Router("/" -> routes).orNotFound)

      (if (withSsl) builder.withSslContext(serverSslContext) else builder)
        .bindHttp(0, "localhost")
        .resource
    }

    server
      .use { server0 =>
        assert(server0.baseUri.renderString.startsWith(if (withSsl) "https://" else "http://"))
        IO(f(server0.baseUri))
      }
      .unsafeRunSync()
  }

  def authorized(req: Request[IO], userPass: (String, String)): Boolean = {

    val res = for {
      token <- req.headers.get[Authorization].map(_.credentials).collect {
        case t: org.http4s.Credentials.Token => t
      }
      if token.authScheme == CIString("Basic")
      c = BasicCredentials(token.token)
      if (c.username, c.password) == userPass
    } yield ()

    res.nonEmpty
  }

  def unauth(realm: String, challenge: String = "Basic", params: Map[String, String] = Map.empty) =
    Unauthorized(`WWW-Authenticate`(NonEmptyList.one(Challenge(challenge, realm, params))))

  implicit class UserUriOps(private val uri: Uri) extends AnyVal {
    def withUser(userOpt: Option[String]): Uri =
      uri.copy(
        authority = uri.authority.map { authority =>
          authority.copy(
            userInfo = userOpt.map(s => Uri.UserInfo(s, None))
          )
        }
      )
  }

  def artifact(uri: Uri, changing: Boolean): Artifact = {
    val (uri0, authOpt) = uri.userInfo match {
      case Some(info) =>
        val updatedUri = uri.copy(authority = uri.authority.map(_.copy(userInfo = None)))
        (updatedUri, Some(Authentication(info.username)))
      case None =>
        (uri, None)
    }
    Artifact(uri0.renderString, Map(), Map(), changing = changing, optional = false, authOpt)
  }

  implicit def artifact(uri: Uri): Artifact =
    artifact(uri, changing = false)

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTmpDir[T](f: os.Path => T): T = {
    val dir = os.temp.dir(prefix = "coursier-test")
    try f(dir)
    finally os.remove.all(dir)
  }

  def withTmpDir0[T](f: Path => T): T = {
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

  def resourceFile(name: String): File =
    Option(getClass.getResource(name)) match {
      case Some(url) => new File(url.toURI)
      case None      => throw new Exception(s"resource $name not found")
    }

  /** Copies a file and all files in the same folder with the same name plus a suffix
    * @return
    *   `file` in the new location
    */
  def copiedWithMetaTo(file: File, toDir: Path): Path = {
    val dir = file.getParentFile

    val fromTo: Map[File, Path] =
      dir
        .list((_: File, name: String) => name.startsWith(file.getName))
        .map(name => new File(dir, name) -> toDir.resolve(name))
        .toMap

    fromTo.foreach { case (from, to) => Files.copy(from.toPath, to) }

    fromTo(file)
  }

  private def checksum(b: Array[Byte], alg: String, len: Int): String = {
    val md     = MessageDigest.getInstance(alg)
    val digest = md.digest(b)
    val res    = new BigInteger(1, digest).toString(16)
    if (res.length < len)
      ("0" * (len - res.length)) + res
    else
      res
  }

  def sha256(b: Array[Byte]): String =
    checksum(b, "SHA-256", 64)
  def sha1(b: Array[Byte]): String =
    checksum(b, "SHA-1", 40)
  def md5(b: Array[Byte]): String =
    checksum(b, "MD5", 32)

}
