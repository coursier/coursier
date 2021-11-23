package coursier.cache

import java.io.{ByteArrayOutputStream, File}
import java.net.{URI, URL, URLClassLoader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.zip.GZIPOutputStream

import cats.effect.IO
import coursier.cache.TestUtil._
import coursier.credentials.{Credentials, DirectCredentials}
import coursier.paths.Util
import coursier.util.{Artifact, Sync, Task}
import org.http4s.dsl.io._
import org.http4s.headers.{Authorization, Location}
import org.http4s.{Header, HttpService, Response, Uri}
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext
import scala.util.Try

object FileCacheTests extends TestSuite {

  private val pool        = Sync.fixedThreadPool(4)
  private implicit val ec = ExecutionContext.fromExecutorService(pool)

  override def utestAfterAll() = {
    ec.shutdown()
    pool.shutdown()
  }

  private def fileCache0() = FileCache()
    .noCredentials
    .withSslSocketFactory(dummyClientSslContext.getSocketFactory)
    .withHostnameVerifier(dummyHostnameVerifier)

  private def expect(
    artifact: Artifact,
    content: String,
    transform: FileCache[Task] => FileCache[Task]
  ): Unit =
    withTmpDir { dir =>
      val c = fileCache0()
        .withLocation(dir.toFile)
      val res         = transform(c).fetch(artifact).run.unsafeRun()
      val expectedRes = Right(content)
      assert(res == expectedRes)
    }

  private def expect(artifact: Artifact, content: String): Unit =
    expect(artifact, content, c => c)

  private def expect(
    uri: Uri,
    content: String,
    transform: FileCache[Task] => FileCache[Task]
  ): Unit =
    expect(artifact(uri), content, transform)

  private def expect(uri: Uri, content: String): Unit =
    expect(artifact(uri), content, c => c)

  private def error(
    artifact: Artifact,
    check: String => Boolean,
    transform: FileCache[Task] => FileCache[Task]
  ): Unit =
    withTmpDir { dir =>
      val c = fileCache0()
        .withLocation(dir.toFile)
      val res = transform(c).fetch(artifact).run.unsafeRun()
      assert(res.isLeft)
      assert(res.left.exists(check))
    }

  private def error(artifact: Artifact, check: String => Boolean): Unit =
    error(artifact, check, c => c)

  private def error(
    uri: Uri,
    check: String => Boolean,
    transform: FileCache[Task] => FileCache[Task]
  ): Unit =
    error(artifact(uri), check, transform)

  private def error(uri: Uri, check: String => Boolean): Unit =
    error(artifact(uri), check, c => c)

  private def credentials(base: Uri, userPass: (String, String)): DirectCredentials =
    Credentials(base.host.fold("")(_.value), userPass._1, userPass._2)

  val tests = Tests {

    test("redirections") {

      test("httpToHttp") {

        def routes(resp: Location => IO[Response[IO]]): HttpService[IO] =
          HttpService[IO] {
            case GET -> Root / "hello"    => Ok("hello")
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

      test("httpsToHttps") {
        val routes = HttpService[IO] {
          case GET -> Root / "hello" => Ok("hello")
          case GET -> Root / "redirect" =>
            TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))
        }
        withHttpServer(routes, withSsl = true) { base =>
          expect(base / "redirect", "hello")
        }
      }

      test("httpToHttps") {

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

        test("enabled") {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "redirect",
              "hello secure"
            )
          }
        }

        test("disabled") {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "redirect",
              "redirecting",
              _.withFollowHttpToHttpsRedirections(false)
            )
          }
        }
      }

      test("httpToAuthHttps") {

        val realm    = "secure realm"
        val userPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "auth-redirect" =>
              TemporaryRedirect(
                "redirecting",
                Location(httpsBaseOpt.getOrElse(???) / "auth" / "hello")
              )
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

        test("enabled") {
          withServers { (httpBaseUri, httpsBaseUri) =>
            expect(
              httpBaseUri / "auth-redirect",
              "hello auth secure",
              _.addCredentials(
                credentials(httpsBaseUri, userPass)
                  .withRealm(realm)
                  .withMatchHost(true)
              )
            )
          }
        }

        test("disabled") {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth-redirect",
              "redirecting",
              _.withFollowHttpToHttpsRedirections(false)
            )
          }
        }
      }

      test("httpToAuthHttp") {

        val realm    = "simple realm"
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

        test("enabled") {
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

        test("enabledAllRealms") {
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

        test("disabled") {
          test {
            withHttpServer(routes) { base =>
              error(
                base / "redirect",
                _.startsWith("unauthorized: ")
              )
            }
          }

          test {
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

      test("authHttpToAuthHttp") {
        val realm    = "simple realm"
        val userPass = ("simple", "SiMpLe")

        def routes(challengeParams: Map[String, String] = Map.empty) = HttpService[IO] {
          case req @ GET -> Root / "redirect" =>
            if (authorized(req, userPass))
              TemporaryRedirect("redirecting", Location(Uri(path = "/hello")))
            else
              unauth(realm)
          case req @ GET -> Root / "hello" =>
            if (authorized(req, userPass))
              Ok("hello auth")
            else
              unauth(realm, params = challengeParams)
        }

        def testEnabled(challengeParams: Map[String, String] = Map.empty) =
          withHttpServer(routes(challengeParams)) { base =>
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

        def testEnabledAllRealms(challengeParams: Map[String, String] = Map.empty) =
          withHttpServer(routes(challengeParams)) { base =>
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

        def testEnabledSeveralCreds(challengeParams: Map[String, String] = Map.empty) =
          withHttpServer(routes(challengeParams)) { base =>
            expect(
              base / "redirect",
              "hello auth",
              _.addCredentials(
                credentials(base, userPass)
                  .withRealm(realm)
                  .withHttpsOnly(false)
                  .withMatchHost(true),
                credentials(
                  base.copy(authority = base.authority.map(a => a.copy(port = a.port.map(_ + 1)))),
                  ("something", "pass123")
                )
                  .withRealm("other realm")
                  .withMatchHost(true)
              )
            )
          }

        def testDisabled(challengeParams: Map[String, String] = Map.empty) =
          withHttpServer(routes(challengeParams)) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }

        test("oldRfc2617") {
          test("enabled") {
            testEnabled()
          }
          test("enabledAllRealms") {
            testEnabledAllRealms()
          }
          test("enabledSeveralCreds") {
            testEnabledSeveralCreds()
          }
          test("disabled") {
            testDisabled()
          }
        }

        test("rfc7617WithCharset") { // current (2020/2021) Sonatype Nexus Repository Manager implements this
          val challengeParams = Map("charset" -> "UTF-8") // RFC 7617 only allows UTF-8

          test("enabled") {
            testEnabled(challengeParams)
          }
          test("enabledAllRealms") {
            testEnabledAllRealms(challengeParams)
          }
          test("enabledSeveralCreds") {
            testEnabledSeveralCreds(challengeParams)
          }
          test("disabled") {
            testDisabled(challengeParams)
          }
        }

        test("beyondRfc7617") { // this tests that the underlying challenge-parsing code is robust enough
          val challengeParams = Map(
            "schtroumpf" -> "salsepareille",
            "charset"    -> "iso-8859-15", // out of RFC 7617
            "abc"        -> "def"
          )
          test("enabled") {
            testEnabled(challengeParams)
          }
          test("enabledAllRealms") {
            testEnabledAllRealms(challengeParams)
          }
          test("enabledSeveralCreds") {
            testEnabledSeveralCreds(challengeParams)
          }
          test("disabled") {
            testDisabled(challengeParams)
          }
        }
      }

      test("httpsToAuthHttps") {

        val realm    = "secure realm"
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

        test("enabled") {
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

        test("enabledAllRealms") {
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

        test("disabled") {
          withHttpServer(routes, withSsl = true) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }
        }
      }

      test("authHttpsToAuthHttps") {

        val realm    = "secure realm"
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

        test("enabled") {
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

        test("enabledAllRealms") {
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

        test("disabled") {
          withHttpServer(routes, withSsl = true) { base =>
            error(
              base / "redirect",
              _.startsWith("unauthorized: ")
            )
          }
        }
      }

      test("authHttpToNoAuthHttps") {

        val httpRealm  = "simple realm"
        val httpsRealm = "secure realm"

        val httpUserPass  = ("simple", "SiMpLe")
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

        test("enabled") {
          test {
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

          test {
            withServers { (httpBaseUri, httpsBaseUri) =>
              val cred = credentials(httpBaseUri, httpUserPass)
                .withHttpsOnly(false)
              expect(
                (httpBaseUri / "redirect")
                  .withUser(cred.usernameOpt),
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

        test("enabledAllRealms") {
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

        test("disabled") {
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

      test("credentialFile") {

        val httpRealm  = "simple realm"
        val httpsRealm = "secure realm"

        val httpUserPass  = ("simple", "SiMpLe")
        val httpsUserPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "auth-redirect" =>
              TemporaryRedirect(
                "redirecting",
                Location(httpsBaseOpt.getOrElse(???) / "auth" / "hello")
              )
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

        val credFile = TestUtil.resourceFile("/credentials.properties")
        assert(credFile.exists())

        test {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth-redirect",
              "hello auth secure",
              _.withFollowHttpToHttpsRedirections(true)
                .addFileCredentials(credFile)
            )
          }
        }

        test {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth" / "redirect",
              "hello auth",
              _.addFileCredentials(credFile)
            )
          }
        }

      }

      test("ransomCase") {

        val httpRealm  = "simple realm"
        val httpsRealm = "secure realm"

        val httpUserPass  = ("simple", "SiMpLe")
        val httpsUserPass = ("secure", "sEcUrE")

        def withServers[T](f: (Uri, Uri) => T): T = {

          var httpsBaseOpt = Option.empty[Uri]

          val httpRoutes = HttpService[IO] {
            case GET -> Root / "auth-redirect" =>
              TemporaryRedirect(
                "redirecting",
                Location(httpsBaseOpt.getOrElse(???) / "auth" / "hello")
              )
            case req @ GET -> Root / "auth" / "redirect" =>
              if (authorized(req, httpUserPass))
                TemporaryRedirect("redirecting", Location(Uri(path = "/auth/hello")))
              else
                unauth(httpRealm)
            case req @ GET -> Root / "auth" / "hello" =>
              if (authorized(req, httpUserPass))
                Ok("hello auth")
              else
                unauth(httpRealm, "bAsIc")
          }

          val httpsRoutes = HttpService[IO] {
            case req @ GET -> Root / "auth" / "hello" =>
              if (authorized(req, httpsUserPass))
                Ok("hello auth secure")
              else
                unauth(httpsRealm, "BAsiC")
          }

          withHttpServer(httpRoutes) { httpBase =>
            withHttpServer(httpsRoutes, withSsl = true) { httpsBase =>
              httpsBaseOpt = Some(httpsBase)
              f(httpBase, httpsBase)
            }
          }
        }

        val credFile = TestUtil.resourceFile("/credentials.properties")
        assert(credFile.exists())

        test {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth-redirect",
              "hello auth secure",
              _.withFollowHttpToHttpsRedirections(true)
                .addFileCredentials(credFile)
            )
          }
        }

        test {
          withServers { (httpBaseUri, _) =>
            expect(
              httpBaseUri / "auth" / "redirect",
              "hello auth",
              _.addFileCredentials(credFile)
            )
          }
        }

      }

      test("maxRedirects") {

        val httpRoutes = HttpService[IO] {
          case GET -> Root / "hello" =>
            Ok("hello")
          case GET -> Root / "redirect" / n if Try(n.toInt).isSuccess =>
            val n0 = n.toInt
            val dest =
              if (n0 <= 0) "/hello"
              else s"/redirect/${n0 - 1}"
            TemporaryRedirect("redirecting", Location(Uri(path = dest)))
        }

        test("should be followed") {
          test - withHttpServer(httpRoutes) { base =>
            error(
              base / "redirect" / "5",
              _ => true,
              _.withMaxRedirections(3)
            )
          }

          test - withHttpServer(httpRoutes) { base =>
            error(
              base / "redirect" / "5",
              _ => true,
              _.withMaxRedirections(5)
            )
          }

          test - withHttpServer(httpRoutes) { base =>
            expect(
              base / "redirect" / "5",
              "hello",
              _.withMaxRedirections(6)
            )
          }
        }

        test("should not stackoverflow") {
          test - withHttpServer(httpRoutes) { base =>
            expect(
              base / "redirect" / "10000",
              "hello",
              _.withMaxRedirections(None)
            )
          }
        }
      }

      test("passCredentialsOnRedirect") {
        val realm    = "secure realm"
        val userPass = ("secure", "sEcUrE")

        def withServers[T](secondServerUseSsl: Boolean = true)(f: (Uri, Uri) => T): T = {

          var base2Opt = Option.empty[Uri]

          val routes1 = HttpService[IO] {
            case GET -> Root / "redirect" =>
              TemporaryRedirect("redirecting", Location(base2Opt.getOrElse(???) / "hello"))
          }

          val routes2 = HttpService[IO] {
            case req @ GET -> Root / "hello" =>
              if (authorized(req, userPass))
                Ok("hello")
              else
                unauth(realm)
          }

          withHttpServer(routes1, withSsl = true) { base1 =>
            withHttpServer(routes2, withSsl = secondServerUseSsl) { base2 =>
              base2Opt = Some(base2)
              f(base1, base2)
            }
          }
        }

        def artifact(base: Uri)(f: DirectCredentials => DirectCredentials) =
          TestUtil.artifact(base / "redirect").withAuthentication(
            Some(f(credentials(base, userPass)).authentication)
          )

        // both servers have the same host here, so we're passing an Authentication ourselves via an Artifact

        test("enabled") {
          test {
            withServers() { (base, _) =>
              expect(
                artifact(base)(
                  _.withPassOnRedirect(true)
                ),
                "hello"
              )
            }
          }

          test {
            withServers(secondServerUseSsl = false) { (base, _) =>
              expect(
                artifact(base)(
                  _.withPassOnRedirect(true).withHttpsOnly(false)
                ),
                "hello",
                _.withFollowHttpsToHttpRedirections(true)
              )
            }
          }
        }

        test("disabled") {
          test {
            withServers() { (base, _) =>
              error(
                artifact(base)(identity),
                _.startsWith("unauthorized: ")
              )
            }
          }

          test {
            withServers(secondServerUseSsl = false) { (base, _) =>
              expect(
                artifact(base)(
                  _.withPassOnRedirect(true) // shouldn't be passed to http redirection by default
                ),
                "redirecting"
              )
            }
          }
        }
      }

      test("decodeGzip") {
        val data       = new ByteArrayOutputStream
        val gzipStream = new GZIPOutputStream(data)
        gzipStream.write("hello".getBytes(StandardCharsets.UTF_8))
        gzipStream.close()

        val routes = HttpService[IO] {
          case GET -> Root / "hello.txt" =>
            Ok(data.toByteArray).map(_.putHeaders(
              Header("Content-Encoding", "gzip")
            ))
        }

        withHttpServer(routes) { base =>
          expect(base / "hello.txt", "hello")
        }
      }

      test("authThenNotFound") {

        val realm    = "secure realm"
        val userPass = ("secure", "sEcUrE")

        val routes = HttpService[IO] {
          case req @ GET -> Root / "hello" =>
            if (authorized(req, userPass))
              NotFound("not found")
            else
              unauth(realm)
        }

        withHttpServer(routes, withSsl = true) { base =>
          error(
            base / "hello",
            _.startsWith("not found: "),
            _.addCredentials(
              credentials(base, userPass)
                .withRealm(realm)
                .withMatchHost(true)
            )
          )
        }
      }
    }

    test("custom protocols") {
      test("unknown") - async {
        val artifact = Artifact("unknown.protocol://hostname/file.txt")

        val res = await {
          FileCache()
            .file(artifact)
            .run
            .future
        }

        val expectedReason =
          List(
            "Caught java.net.MalformedURLException (unknown protocol: unknown.protocol) while downloading unknown.protocol://hostname/file.txt.",
            "Visit https://get-coursier.io/docs/extra.html#extra-protocols to learn how to handle custom protocols."
          ).mkString(" ")

        res match {
          case Left(error: ArtifactError.DownloadError) =>
            assert(error.reason == expectedReason)

          case _ =>
            assert(false)
        }
      }

      test("with classloader") {
        withTmpDir { dir =>
          async {
            val classloader =
              new URLClassLoader(
                CustomLoaderClasspath.files.map(new URL(_)).toArray
              )

            val artifact = Artifact("customprotocol://hostname/README.md")

            val res = await {
              FileCache()
                .withClassLoaders(Seq(classloader))
                .withLocation(dir.toFile)
                .file(artifact)
                .run
                .future
            }

            res match {
              case Right(file) =>
                val actual   = new String(Files.readAllBytes(file.toPath))
                val expected = new String(Files.readAllBytes(Paths.get("README.md")))
                assert(actual == expected)

              case Left(e) => throw e
            }
          }
        }
      }
    }

    test("checksums") {

      test("simple") {
        val dummyFileUri = TestUtil.resourceFile("/data/foo.xml").toURI.toASCIIString

        val artifact = Artifact(
          dummyFileUri,
          Map(
            "SHA-512" -> s"$dummyFileUri.sha512", // should not exist
            "SHA-256" -> s"$dummyFileUri.sha256", // should not exist
            "SHA-1"   -> s"$dummyFileUri.sha1",
            "MD5"     -> s"$dummyFileUri.md5"
          ),
          Map(),
          changing = false,
          optional = false,
          None
        )

        test - async {
          val res = await {
            FileCache()
              .withChecksums(Seq(Some("SHA-1")))
              .file(artifact)
              .run
              .future()
          }

          assert(res.isRight)
        }

        test - async {
          val res = await {
            FileCache()
              .withChecksums(Seq(Some("SHA-256")))
              .file(artifact)
              .run
              .future()
          }

          val expectedErrors = Seq(
            "SHA-256" -> s"not found: ${new File(new URI(dummyFileUri + ".sha256"))}"
          )

          assert(res.isLeft)
          assert(res.left.exists {
            case err: ArtifactError.ChecksumErrors =>
              err.errors == expectedErrors
            case _ => ???
          })
        }

        test - async {
          val res = await {
            FileCache()
              .withChecksums(Seq(Some("SHA-512"), Some("SHA-256")))
              .file(artifact)
              .run
              .future()
          }

          val expectedErrors = Seq(
            "SHA-512" -> s"not found: ${new File(new URI(dummyFileUri + ".sha512"))}",
            "SHA-256" -> s"not found: ${new File(new URI(dummyFileUri + ".sha256"))}"
          )

          assert(res.isLeft)
          assert(res.left.exists {
            case err: ArtifactError.ChecksumErrors =>
              err.errors == expectedErrors
            case _ => ???
          })
        }
      }

      test("fromHeader") {

        val content = "ok\n"
        val b       = content.getBytes(StandardCharsets.UTF_8)
        val sha256  = TestUtil.sha256(b)
        val sha1    = TestUtil.sha1(b)
        val md5     = TestUtil.md5(b)

        val routes = HttpService[IO] {
          case GET -> Root / "foo.txt" =>
            Ok(b).map(_.putHeaders(
              Header("X-Checksum-SHA256", sha256),
              Header("X-Checksum-SHA1", sha1),
              Header("X-Checksum-MD5", md5)
            ))
        }

        def artifact(uri: Uri): Artifact =
          Artifact(
            uri.renderString,
            Map(
              // no SHA-256 entry - must work fine despite that
              "SHA-1" -> uri.withPath(uri.path + ".sha1").renderString,
              "MD5"   -> uri.withPath(uri.path + ".md5").renderString
            ),
            Map.empty,
            changing = false,
            optional = false,
            None
          )

        "SHA-256" - withHttpServer(routes) { root =>
          expect(artifact(root / "foo.txt"), content, _.withChecksums(Seq(Some("SHA-256"))))
        }
        "SHA-1" - withHttpServer(routes) { root =>
          expect(artifact(root / "foo.txt"), content, _.withChecksums(Seq(Some("SHA-1"))))
        }
        "MD5" - withHttpServer(routes) { root =>
          expect(artifact(root / "foo.txt"), content, _.withChecksums(Seq(Some("MD5"))))
        }
      }
    }

    test("lastModifiedEx") {
      withTmpDir { dir =>
        val url       = "https://foo-does-no-exist-zzzzzzz/a.pom"
        val cacheFile = dir.resolve(url.replace("://", "/"))
        Util.createDirectories(cacheFile.getParent)
        Files.write(cacheFile, Array.emptyByteArray)
        val c = fileCache0()
          .withLocation(dir.toFile)
          .withTtl(None)
          .withCachePolicies(Seq(
            CachePolicy.LocalUpdateChanging
          ))
        val res = c.fetch(artifact(Uri.unsafeFromString(url), changing = true)).run.unsafeRun()
        assert(res.left.exists(_.contains("java.net.UnknownHostException")))
      }
    }

    test("stored digests work - SHA1") {
      withTmpDir { dir =>
        val dummyFile    = TestUtil.copiedWithMetaTo(TestUtil.resourceFile("/data/foo.xml"), dir)
        val dummyFileUri = dummyFile.toUri.toASCIIString
        val artifact = Artifact(
          dummyFileUri,
          Map(
            "SHA-512" -> s"$dummyFileUri.sha512", // should not exist
            "SHA-256" -> s"$dummyFileUri.sha256", // should not exist
            "SHA-1"   -> s"$dummyFileUri.sha1",
            "MD5"     -> s"$dummyFileUri.md5"
          ),
          Map(),
          changing = false,
          optional = false,
          None
        )

        val res =
          FileCache()
            .withLocation(dir.toString)
            .withChecksums(Seq(Some("SHA-1")))
            .file(artifact)
            .run
            .unsafeRun()

        res match {
          case Right(file: File) =>
            val computedPath = FileCache.auxiliaryFile(file, "SHA-1" + ".computed")
            val expected     = stringToByteArray("f9627d29027e5a853b65242cfbbb44f354f3836f")
            val actual       = Files.readAllBytes(computedPath.toPath)
            assert(util.Arrays.equals(actual, expected))
          case Left(e) => throw e
        }
      }
    }

    test("stored digests work - MD5") {
      withTmpDir { dir =>
        val dummyFile    = TestUtil.copiedWithMetaTo(TestUtil.resourceFile("/data/foo.xml"), dir)
        val dummyFileUri = dummyFile.toUri.toASCIIString
        val artifact = Artifact(
          dummyFileUri,
          Map(
            "SHA-512" -> s"$dummyFileUri.sha512", // should not exist
            "SHA-256" -> s"$dummyFileUri.sha256", // should not exist
            "SHA-1"   -> s"$dummyFileUri.sha1",
            "MD5"     -> s"$dummyFileUri.md5"
          ),
          Map(),
          changing = false,
          optional = false,
          None
        )

        val res = FileCache()
          .withLocation(dir.toString)
          .withChecksums(Seq(Some("MD5")))
          .file(artifact)
          .run
          .unsafeRun()

        res match {
          case Right(file: File) =>
            val computedPath = FileCache.auxiliaryFile(file, "MD5" + ".computed")
            val expected     = stringToByteArray("001717e73bca14e4fb2df3cabd6eac98")
            val actual       = Files.readAllBytes(computedPath.toPath)
            assert(util.Arrays.equals(actual, expected))
          case Left(e) => throw e
        }
      }
    }

    test("stored digests should not be stored outside of cache") {
      withTmpDir { dir =>
        val dummyFile    = TestUtil.copiedWithMetaTo(TestUtil.resourceFile("/data/foo.xml"), dir)
        val dummyFileUri = dummyFile.toUri.toASCIIString
        val artifact = Artifact(
          dummyFileUri,
          Map(
            "SHA-512" -> s"$dummyFileUri.sha512", // should not exist
            "SHA-256" -> s"$dummyFileUri.sha256", // should not exist
            "SHA-1"   -> s"$dummyFileUri.sha1",
            "MD5"     -> s"$dummyFileUri.md5"
          ),
          Map(),
          changing = false,
          optional = false,
          None
        )

        // use default location so our file is considered outside
        val _ = FileCache()
          .withChecksums(Seq(Some("MD5")))
          .file(artifact)
          .run
          .unsafeRun()

        val computedPath = FileCache.auxiliaryFile(dummyFile.toFile, "MD5" + ".computed")
        assert(!computedPath.exists())
      }
    }

    test("wrong stored digest should delete file in cache") {
      withTmpDir { dir =>
        val dummyFile    = TestUtil.copiedWithMetaTo(TestUtil.resourceFile("/data/foo.xml"), dir)
        val dummyFileUri = dummyFile.toUri.toASCIIString

        val resolve = {
          val artifact = Artifact(
            dummyFileUri,
            Map("SHA-1" -> s"$dummyFileUri.sha1"),
            Map(),
            changing = false,
            optional = false,
            None
          )

          FileCache()
            .withLocation(dir.toString)
            .withChecksums(Seq(Some("SHA-1")))
            .file(artifact)
            .run
        }

        val Right(_)         = resolve.unsafeRun()
        val computedSha1Path = FileCache.auxiliaryFile(dummyFile.toFile, "SHA-1" + ".computed")

        Files.write(computedSha1Path.toPath, Array[Byte](1, 2, 3))

        val Left(_: coursier.cache.ArtifactError.NotFound) = resolve.unsafeRun()
      }
    }

    test("un-escape characters in file URL") {
      withTmpDir { baseDir =>
        val dir = baseDir.resolve("le repository")
        Files.createDirectories(dir)
        val dummyFile    = TestUtil.copiedWithMetaTo(TestUtil.resourceFile("/data/foo.xml"), dir)
        val dummyFileUri = dummyFile.toUri.toASCIIString
        assert(dummyFileUri.contains("%20"))
        val artifact = Artifact(dummyFileUri)
          .withChecksumUrls(Map("SHA-1" -> s"$dummyFileUri.sha1"))

        val res = FileCache()
          .withLocation(dir.toString)
          .file(artifact)
          .run
          .unsafeRun()

        res match {
          case Right(file) => assert(file.isFile)
          case Left(e)     => throw e
        }
      }
    }
  }

  // https://stackoverflow.com/questions/6650650/hex-encoded-string-to-byte-array/28157958#28157958
  def stringToByteArray(s: String): Array[Byte] = {
    val byteArray = new Array[Byte](s.length / 2)
    val strBytes  = new Array[String](s.length / 2)
    var k         = 0
    var i         = 0
    while (i < s.length) {
      val j = i + 2
      strBytes(k) = s.substring(i, j)
      byteArray(k) = Integer.parseInt(strBytes(k), 16).toByte
      k += 1

      i = i + 2
    }
    byteArray
  }
}
