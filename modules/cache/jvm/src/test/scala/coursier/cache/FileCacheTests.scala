package coursier.cache

import cats.effect.IO
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Uri}
import utest._

import java.time._
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

object FileCacheTests extends TestSuite {

  val tests = Tests {

    test("check for updates once") {

      val start       = LocalDateTime.of(2022, 12, 12, 9, 0, 0)
      val offset      = ZoneOffset.UTC
      val firstCheck  = start.toInstant(offset)
      val secondCheck = start.plusDays(1L).toInstant(offset)
      val thirdCheck  = start.plusDays(2L).toInstant(offset)
      val fourthCheck = start.plusDays(2L).plusHours(12L).toInstant(offset)

      val firstContent  = "first pom"
      val secondContent = "second pom"
      val thirdContent  = "third pom"
      val fourthContent = "fourth pom"

      var serverContent = firstContent
      val routes        = HttpRoutes.of[IO] {
        case GET -> Root / "dir" / "foo.pom" =>
          Ok(serverContent)
      }

      withHttpServer(routes) { serverUri =>
        withTmpDir { dir =>

          val cache = FileCache[Task]((dir / "cache").toIO)
            .withTtl(Duration(36L, TimeUnit.HOURS))
          val artifact = Artifact((serverUri / "dir" / "foo.pom").renderString)
            .withChanging(true)

          def check(instant: Instant, serving: String, expected: String = null): Unit = {
            val expected0 = Option(expected).getOrElse(serving)
            serverContent = serving
            val file = cache
              .withClock(Clock.fixed(instant, offset))
              .file(artifact).run
              .unsafeRun(wrapExceptions = true)(cache.ec)
              .fold(e => throw new Exception(e), identity)
            val content = os.read(os.Path(file))
            assert(content == expected0)
          }

          check(firstCheck, firstContent)

          // check less than TTL later - no update
          check(secondCheck, secondContent, firstContent)

          // check more than TTL later - should be updated
          check(thirdCheck, thirdContent)

          // check less than TTL later - no update
          check(fourthCheck, fourthContent, thirdContent)
        }
      }
    }

    test("check extra artifact") {

      def routes(enableModule: Boolean) = HttpRoutes.of[IO] {
        case GET -> Root / "dir" / "foo.pom" =>
          Ok("the pom")
        case GET -> Root / "dir" / "foo.module" if enableModule =>
          Ok("the module")
      }

      def check(
        cache: FileCache[Task],
        dir: os.Path
      )(artifact: Artifact, expectedOpt: Option[String]): Unit = {
        val fileOrError = cache
          .file(artifact).run
          .unsafeRun(wrapExceptions = true)(cache.ec)
        expectedOpt match {
          case Some(expected) =>
            val file = fileOrError
              .fold(e => throw new Exception(e), identity)
            val content = os.read(os.Path(file))
            assert(content == expected)
          case None =>
            fileOrError match {
              case Left(_: ArtifactError.NotFound)                  =>
              case Left(_: ArtifactError.MissingOtherArtifactCheck) =>
              case Left(other)                                      =>
                throw new Exception(other)
              case Right(content) =>
                sys.error(s"Unexpected content at ${artifact.url}: ${os.read(os.Path(content))}")
            }
        }
      }

      def basePomArtifact(serverUri: Uri) =
        Artifact((serverUri / "dir" / "foo.pom").renderString)
      def baseModuleArtifact(serverUri: Uri) =
        Artifact((serverUri / "dir" / "foo.module").renderString)

      def validateCacheState(
        serverUri: Uri,
        cache: FileCache[Task]
      )(
        expected: Seq[(os.SubPath, String)]
      ): Unit = {
        val baseDir =
          os.Path(FileCache.localFile0(serverUri.renderString, cache.location, None, false)) / os.up

        val inCache = os.walk(baseDir)
          .filter(os.isFile)
          .map(p => (p.relativeTo(baseDir).asSubPath, os.read(p)))
          .sortBy(_._1)
        if (expected != inCache) {
          pprint.err.log(expected)
          pprint.err.log(inCache)
        }
        assert(expected == inCache)
      }

      test {
        withHttpServer(routes(enableModule = false)) { serverUri =>
          withTmpDir { dir =>

            val cache = FileCache[Task]((dir / "cache").toIO)

            val basePomArtifact0    = basePomArtifact(serverUri)
            val baseModuleArtifact0 = baseModuleArtifact(serverUri)

            val check0 = check(cache, dir) _

            // first put the POM in cache, as if downloaded by an older coursier version
            check0(basePomArtifact0, Some("the pom"))
            // then try to download the module, without metadata: no error file should be written for it
            check0(baseModuleArtifact0, None)

            validateCacheState(serverUri, cache)(
              Seq(
                (os.sub / "dir" / ".foo.pom.checked") -> "",
                (os.sub / "dir" / "foo.pom")          -> "the pom"
              )
            )
          }
        }
      }

      test {
        withHttpServer(routes(enableModule = false)) { serverUri =>
          withTmpDir { dir =>

            val cache = FileCache[Task]((dir / "cache").toIO)

            val basePomArtifact0    = basePomArtifact(serverUri)
            val baseModuleArtifact0 = baseModuleArtifact(serverUri)

            val check0 = check(cache, dir) _

            // first put the POM in cache, as if downloaded by an older coursier version
            check0(basePomArtifact0, Some("the pom"))
            // then try to download the module: this should be remembered via a '.error' file
            check0(baseModuleArtifact0.withExtra(Map("metadata" -> basePomArtifact0)), None)

            validateCacheState(serverUri, cache)(
              Seq(
                (os.sub / "dir" / ".foo.module.error") -> "",
                (os.sub / "dir" / ".foo.pom.checked")  -> "",
                (os.sub / "dir" / "foo.pom")           -> "the pom"
              )
            )
          }
        }
      }

      test {
        withHttpServer(routes(enableModule = true)) { serverUri =>
          withTmpDir { dir =>

            val cache = FileCache[Task]((dir / "cache").toIO)

            val basePomArtifact0    = basePomArtifact(serverUri)
            val baseModuleArtifact0 = baseModuleArtifact(serverUri)

            // first put the POM in cache, as if downloaded by an older coursier version
            check(cache, dir)(basePomArtifact0, Some("the pom"))
            validateCacheState(serverUri, cache)(
              Seq(
                (os.sub / "dir" / ".foo.pom.checked") -> "",
                (os.sub / "dir" / "foo.pom")          -> "the pom"
              )
            )

            // then try to download it again in offline mode, requiring a module check - this should fail
            check(cache.withCachePolicies(Seq(CachePolicy.LocalUpdateChanging)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              None
            )

            // then try to download it while online, requiring a module check - this should fail too
            check(cache.withCachePolicies(Seq(CachePolicy.FetchMissing)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              None
            )

            // now download the module file
            check(cache, dir)(baseModuleArtifact0, Some("the module"))

            // then try to download the pom again in offline mode, requiring a module check - this should succeed this time
            check(cache.withCachePolicies(Seq(CachePolicy.LocalUpdateChanging)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              Some("the pom")
            )
          }
        }
      }

      test {
        withHttpServer(routes(enableModule = true)) { serverUri =>
          withTmpDir { dir =>

            val cache = FileCache[Task]((dir / "cache").toIO)

            val basePomArtifact0    = basePomArtifact(serverUri)
            val baseModuleArtifact0 = baseModuleArtifact(serverUri)

            // try to download the pom in offline mode, requiring a module check - this should fail
            check(cache.withCachePolicies(Seq(CachePolicy.LocalUpdateChanging)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              None
            )

            // then try to download it while online, requiring a module check - this should fail too
            check(cache.withCachePolicies(Seq(CachePolicy.FetchMissing)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              None
            )

            // now download the module file
            check(cache, dir)(baseModuleArtifact0, Some("the module"))

            // then try to download the pom again in offline mode, requiring a module check - this should fail because of offline
            check(cache.withCachePolicies(Seq(CachePolicy.LocalUpdateChanging)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              None
            )

            // then try to download the pom again while online, requiring a module check - this should succeed this time
            check(cache.withCachePolicies(Seq(CachePolicy.FetchMissing)), dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              Some("the pom")
            )
          }
        }
      }

      test {
        withHttpServer(routes(enableModule = false)) { serverUri =>
          withTmpDir { dir =>

            val cache = FileCache[Task]((dir / "cache").toIO)

            val basePomArtifact0    = basePomArtifact(serverUri)
            val baseModuleArtifact0 = baseModuleArtifact(serverUri)

            // try to download the module - this should fail
            check(cache, dir)(
              baseModuleArtifact0.withExtra(Map("cache-errors" -> Artifact(""))),
              None
            )

            // then try to download the pom - this should succeed
            check(cache, dir)(
              basePomArtifact0.withExtra(Map("check" -> baseModuleArtifact0)),
              Some("the pom")
            )
          }
        }
      }
    }
  }

}
