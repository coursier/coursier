package coursier.cache

import java.io.File
import java.net.URI

import cats.effect.IO
import coursier.cache.TestUtil._
import coursier.core.Artifact
import coursier.credentials.{Credentials, DirectCredentials}
import coursier.util.{Sync, Task}
import org.http4s.dsl.io._
import org.http4s.headers.{Authorization, Location}
import org.http4s.{HttpService, Response, Uri}
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

object FileCacheTests extends TestSuite {

  private val pool = Sync.fixedThreadPool(4)
  private implicit val ec = ExecutionContext.fromExecutorService(pool)

  override def utestAfterAll() = {
    ec.shutdown()
    pool.shutdown()
  }

  private def fileCache0() = FileCache()
    .noCredentials
    .withSslSocketFactory(dummyClientSslContext.getSocketFactory)
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

  private def credentials(base: Uri, userPass: (String, String)): DirectCredentials =
    Credentials(base.host.fold("")(_.value), userPass._1, userPass._2)

  val tests = Tests {

    'redirections - {

      'httpToHttp - {

        def routes(resp: Location => IO[Response[IO]]): HttpService[IO] =
          HttpService[IO] {
            case GET -> Root / "hello" => Ok("hello")
            case GET -> Root / "redirect" => resp(Location(Uri(path = "/hello")))
          }
        def test(resp: Location => IO[Response[IO]]): Unit =
          withHttpServer(routes(resp)) { base =>
            expect(base / "redirect", "hello")
          }

        "301" - test(MovedPermanently("redirecting", _))
        "302" - test(Found("redirecting", _))
        "304" - test(NotModified(_))
        "307" - test(TemporaryRedirect("redirecting", _))
        "308" - test(PermanentRedirect("redirecting", _))
      }

      'httpsToHttps - {
        val routes = HttpService[IO] {
          case GET -> Root / "hello" => Ok("hello")
          case GET -> Root / "redirect" =>
            TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))
        }
        withHttpServer(routes, withSsl = true) { base =>
          expect(base / "redirect", "hello")
        }
      }

      'httpToHttps - {

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "redirect" =>
              TemporaryRedirect("redirecting", Location(httpsBaseOpt.getOrElse(???) / "hello"))
          }

          val httpsRoutes = HttpService[IO] {
            case GET -> Root / "hello" => Ok("hello secure")
          }

          withHttpServer(httpRoutes) { httpBase =>
            withHttpServer(httpsRoutes, withSsl = true) { httpsBase =>
              httpsBaseOpt = Some(httpsBase)
              f(httpBase, httpsBase)
            }
          }
        }

        'enabled - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "redirect",
              "hello secure",
              _.withFollowHttpToHttpsRedirections(true)
            )
          }
        }

        'disabled - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "redirect",
              "redirecting"
            )
          }
        }
      }

      'httpToAuthHttps - {

        val realm = "secure realm"
        val userPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "auth-redirect" =>
              TemporaryRedirect("redirecting", Location(httpsBaseOpt.getOrElse(???) / "auth" / "hello"))
          }

          val httpsRoutes = HttpService[IO] {
            case req @ GET -> Root / "auth" / "hello" =>
              if (authorized(req, userPass))
                Ok("hello auth secure")
              else
                unauth(realm)
          }

          withHttpServer(httpRoutes) { httpBase =>
            withHttpServer(httpsRoutes, withSsl = true) { httpsBase =>
              httpsBaseOpt = Some(httpsBase)
              f(httpBase, httpsBase)
            }
          }
        }

        'enabled - {
          withServers { (httpBaseUri, httpsBaseUri) =>
            expect(
              httpBaseUri / "auth-redirect",
              "hello auth secure",
              _.withFollowHttpToHttpsRedirections(true)
                .addCredentials(
                  credentials(httpsBaseUri, userPass)
                    .withRealm(realm)
                    .withMatchHost(true)
                )
            )
          }
        }

        'disabled - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth-redirect",
              "redirecting"
            )
          }
        }
      }

      'httpToAuthHttp - {

        val realm = "simple realm"
        val userPass = ("simple", "SiMpLe")

        val routes = HttpService[IO] {
          case GET -> Root / "redirect" =>
            TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))
          case req @ GET -> Root / "auth" / "hello" =>
            if (authorized(req, userPass))
              Ok("hello auth")
            else
              unauth(realm)
        }

        'enabled - {
          withHttpServer(routes) { base =>

            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withHttpsOnly(false)
                  .withMatchHost(true)
              )
            )
          }
        }

        'enabledAllRealms - {
          withHttpServer(routes) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withHttpsOnly(false)
                  .withRealm(None)
                  .withMatchHost(true)
              )
            )
          }
        }

        'disabled - {
          * - {
            withHttpServer(routes) { base =>
              error(
                base / "redirect",
                _.startsWith("unauthorized: ")
              )
            }
          }

          * - {
            withHttpServer(routes) { base =>
              error(
                base / "redirect",
                _.startsWith("unauthorized: "),
                _.addCredentials(
                  credentials(base, userPass)
                    .withRealm(realm)
                    .withHttpsOnly(true) // should make things fail
                    .withMatchHost(true)
                )
              )
            }
          }
        }
      }

      'authHttpToAuthHttp - {

        val realm = "simple realm"
        val userPass = ("simple", "SiMpLe")

        val routes = HttpService[IO] {
          case req @ GET -> Root / "redirect" =>
            if (authorized(req, userPass))
              TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))
            else
              unauth(realm)
          case req @ GET -> Root / "hello" =>
            if (authorized(req, userPass))
              Ok("hello auth")
            else
              unauth(realm)
        }

        'enabled - {
          withHttpServer(routes) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withHttpsOnly(false)
                  .withMatchHost(true)
              )
            )
          }
        }

        'enabledAllRealms - {
          withHttpServer(routes) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withHttpsOnly(false)
                  .withRealm(None)
                  .withMatchHost(true)
              )
            )
          }
        }

        'enabledSeveralCreds - {
          withHttpServer(routes) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withHttpsOnly(false)
                  .withMatchHost(true),
                credentials(base.copy(authority = base.authority.map(a => a.copy(port = a.port.map(_ + 1)))), ("something", "pass123"))
                  .withRealm("other realm")
                  .withMatchHost(true)
              )
            )
          }
        }

        'disabled - {
          withHttpServer(routes) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }
        }
      }

      'httpsToAuthHttps - {

        val realm = "secure realm"
        val userPass = ("secure", "sEcUrE")

        val routes = HttpService[IO] {
          case GET -> Root / "redirect" =>
            TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))
          case req @ GET -> Root / "auth" / "hello" =>
            if (authorized(req, userPass))
              Ok("hello auth")
            else
              unauth(realm)
        }

        'enabled - {
          withHttpServer(routes, withSsl = true) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withMatchHost(true)
              )
            )
          }
        }

        'enabledAllRealms - {
          withHttpServer(routes, withSsl = true) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(None)
                  .withMatchHost(true)
              )
            )
          }
        }

        'disabled - {
          withHttpServer(routes, withSsl = true) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }
        }
      }

      'authHttpsToAuthHttps - {

        val realm = "secure realm"
        val userPass = ("secure", "sEcUrE")

        val routes = HttpService[IO] {
          case req @ GET -> Root / "redirect" =>
            if (authorized(req, userPass))
              TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))
            else
              unauth(realm)
          case req @ GET -> Root / "hello" =>
            if (authorized(req, userPass))
              Ok("hello auth")
            else
              unauth(realm)
        }

        'enabled - {
          withHttpServer(routes, withSsl = true) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withMatchHost(true)
              )
            )
          }
        }

        'enabledAllRealms - {
          withHttpServer(routes, withSsl = true) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(None)
                  .withMatchHost(true)
              )
            )
          }
        }

        'disabled - {
          withHttpServer(routes, withSsl = true) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }
        }
      }

      'authHttpToNoAuthHttps - {

        val httpRealm = "simple realm"
        val httpsRealm = "secure realm"

        val httpUserPass = ("simple", "SiMpLe")
        val httpsUserPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case req @ GET -> Root / "redirect" =>
              if (authorized(req, httpUserPass))
                TemporaryRedirect("redirecting", Location(httpsBaseOpt.getOrElse(???) / "hello"))
              else
                unauth(httpRealm)
          }

          val httpsRoutes = HttpService[IO] {
            case req @ GET -> Root / "hello" =>
              val authHeaderOpt = req.headers.get(Authorization)
              if (authHeaderOpt.isEmpty)
                Ok("hello")
              else
                BadRequest()
          }

          withHttpServer(httpRoutes) { httpBase =>
            withHttpServer(httpsRoutes, withSsl = true) { httpsBase =>
              httpsBaseOpt = Some(httpsBase)
              f(httpBase, httpsBase)
            }
          }
        }

        'enabled - {
          * - {
            withServers { (httpBaseUri, httpsBaseUri) =>
              expect(
                httpBaseUri / "redirect",
                "hello",
                _
                  .addCredentials(
                    credentials(httpsBaseUri, httpsUserPass)
                      .withRealm(httpsRealm)
                      .withMatchHost(true),
                    credentials(httpBaseUri, httpUserPass)
                      .withRealm(httpRealm)
                      .withHttpsOnly(false)
                      .withMatchHost(true)
                  )
                  .withFollowHttpToHttpsRedirections(true)
              )
            }
          }

          * - {
            withServers { (httpBaseUri, httpsBaseUri) =>
              val cred = credentials(httpBaseUri, httpUserPass)
                .withHttpsOnly(false)
              expect(
                (httpBaseUri / "redirect")
                  .withUser(cred.username),
                "hello",
                _
                  .addCredentials(
                    credentials(httpsBaseUri, httpsUserPass)
                      .withRealm(httpsRealm)
                      .withMatchHost(true),
                    cred
                  )
                  .withFollowHttpToHttpsRedirections(true)
              )
            }
          }
        }

        'enabledAllRealms - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "redirect",
              "hello",
              _.addCredentials(
                credentials(httpBaseUri, httpUserPass)
                  .withHttpsOnly(false)
                  .withRealm(None)
                  .withMatchHost(true)
              )
                .withFollowHttpToHttpsRedirections(true)
            )
          }
        }

        'disabled - {
          withServers { (httpBaseUri, httpsBaseUri) =>
            error(
              httpBaseUri / "redirect",
              _.startsWith("unauthorized: "),
              _
                .addCredentials(
                  credentials(httpsBaseUri, httpsUserPass)
                    .withRealm(httpsRealm)
                    .withMatchHost(true)
                )
                .withFollowHttpToHttpsRedirections(true)
            )
          }
        }
      }

      'credentialFile - {

        val httpRealm = "simple realm"
        val httpsRealm = "secure realm"

        val httpUserPass = ("simple", "SiMpLe")
        val httpsUserPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "auth-redirect" =>
              TemporaryRedirect("redirecting", Location(httpsBaseOpt.getOrElse(???) / "auth" / "hello"))
            case req @ GET -> Root / "auth" / "redirect" =>
              if (authorized(req, httpUserPass))
                TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))
              else
                unauth(httpRealm)
            case req @ GET -> Root / "auth" / "hello" =>
              if (authorized(req, httpUserPass))
                Ok("hello auth")
              else
                unauth(httpRealm)
          }

          val httpsRoutes = HttpService[IO] {
            case req @ GET -> Root / "auth" / "hello" =>
              if (authorized(req, httpsUserPass))
                Ok("hello auth secure")
              else
                unauth(httpsRealm)
          }

          withHttpServer(httpRoutes) { httpBase =>
            withHttpServer(httpsRoutes, withSsl = true) { httpsBase =>
              httpsBaseOpt = Some(httpsBase)
              f(httpBase, httpsBase)
            }
          }
        }

        val credFilePath = Option(getClass.getResource("/credentials.properties"))
          .map(_.getPath)
          .getOrElse {
            throw new Exception("credentials.properties resource not found")
          }
        val credFile = new File(credFilePath)
        assert(credFile.exists())

        * - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth-redirect",
              "hello auth secure",
              _.withFollowHttpToHttpsRedirections(true)
                .addFileCredentials(credFile)
            )
          }
        }

        * - {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth" / "redirect",
              "hello auth",
              _.addFileCredentials(credFile)
            )
          }
        }

      }

    }

    'checksums - {

      val dummyFileUri = Option(getClass.getResource("/data/foo.xml"))
        .map(_.toURI.toASCIIString)
        .getOrElse {
          throw new Exception("data/foo.xml resource not found")
        }

      val artifact = Artifact(
        dummyFileUri,
        Map(
          "SHA-512" -> s"$dummyFileUri.sha512", // should not exist
          "SHA-256" -> s"$dummyFileUri.sha256", // should not exist
          "SHA-1" -> s"$dummyFileUri.sha1",
          "MD5" -> s"$dummyFileUri.md5"
        ),
        Map(),
        changing = false,
        optional = false,
        None
      )

      * - async {
        val res = await {
          FileCache()
            .withChecksums(Seq(Some("SHA-1")))
            .file(artifact)
            .run
            .future()
        }

        assert(res.isRight)
      }

      * - async {
        val res = await {
          FileCache()
            .withChecksums(Seq(Some("SHA-256")))
            .file(artifact)
            .run
            .future()
        }

        val expectedRes = Left(
          ArtifactError.ChecksumErrors(Seq(
            "SHA-256" -> s"not found: ${new File(new URI(dummyFileUri + ".sha256"))}"
          ))
        )

        assert(res.isLeft)
        assert(res == expectedRes)
      }

      * - async {
        val res = await {
          FileCache()
            .withChecksums(Seq(Some("SHA-512"), Some("SHA-256")))
            .file(artifact)
            .run
            .future()
        }

        val expectedRes = Left(
          ArtifactError.ChecksumErrors(Seq(
            "SHA-512" -> s"not found: ${new File(new URI(dummyFileUri + ".sha512"))}",
            "SHA-256" -> s"not found: ${new File(new URI(dummyFileUri + ".sha256"))}"
          ))
        )

        assert(res.isLeft)
        assert(res == expectedRes)
      }
    }
  }

}
