package coursier.cache

import cats.effect.IO
import coursier.cache.TestUtil._
import coursier.paths.CachePath
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
      val routes = HttpRoutes.of[IO] {
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
              .unsafeRun()(cache.ec)
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

  }

}
