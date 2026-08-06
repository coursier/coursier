package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.dsl.io._
import org.http4s.headers.`Last-Modified`
import org.http4s.{HttpDate, HttpRoutes}
import utest._

import java.io.File
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import scala.concurrent.duration.Duration

/** Behavioural baseline for the TTL and the `.checked` file that carries it.
  *
  * Time is driven by the cache's `Clock`, never by sleeping, so these are instant and
  * deterministic.
  */
object FileCacheTtlTests extends TestSuite {

  private val zone = ZoneOffset.UTC

  private val t0 = LocalDateTime.of(2024, 1, 1, 12, 0, 0).toInstant(zone)

  private def hoursIn(h: Long): Instant   = t0.plusSeconds(h * 3600L)
  private def secondsIn(s: Long): Instant = t0.plusSeconds(s)

  private val oneDay = Duration(24L, java.util.concurrent.TimeUnit.HOURS)

  private val lastModified1 =
    HttpDate.unsafeFromInstant(LocalDateTime.of(2023, 1, 1, 0, 0, 0).toInstant(zone))
  private val lastModified2 =
    HttpDate.unsafeFromInstant(LocalDateTime.of(2023, 6, 1, 0, 0, 0).toInstant(zone))

  private val firstContent  = "first pom"
  private val secondContent = "second pom"

  private final class ServerState {
    @volatile var content: String        = firstContent
    @volatile var lastModified: HttpDate = lastModified1

    /** Makes the server serve something newer than what is in cache. */
    def serveNewer(): Unit = {
      content = secondContent
      lastModified = lastModified2
    }
  }

  private def routes(state: ServerState): HttpRoutes[IO] = {
    def respond =
      Ok(state.content).map(_.putHeaders(`Last-Modified`(state.lastModified)))
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.pom"  => respond
      case HEAD -> Root / "dir" / "foo.pom" => respond
    }
  }

  private final class Ctx(
    val state: ServerState,
    val log: RequestLog,
    base: FileCache[Task],
    val url: String
  ) {

    /** Runs one fetch, at `instant`, and returns the requests it issued along with its result. */
    def at(
      instant: Instant,
      policy: CachePolicy,
      changing: Boolean = true
    ): (List[String], Either[ArtifactError, File]) = {
      log.reset()
      val cache = base
        .withCachePolicies(Seq(policy))
        .withClock(Clock.fixed(instant, zone))
      val res = cache
        .file(Artifact(url).withChanging(changing)).run
        .unsafeRun(wrapExceptions = true)(cache.ec)
      (log.methods, res)
    }

    /** Puts the artifact in cache, as of `instant`. */
    def seedAt(instant: Instant): Unit = {
      val (requests, res) = at(instant, CachePolicy.FetchMissing, changing = false)
      assert(requests == List("GET"))
      assert(res.isRight)
    }

    def checkedFile: File = {
      val f = base.localFile(url)
      new File(f.getParentFile, s".${f.getName}.checked")
    }

    def contentOf(res: Either[ArtifactError, File]): String =
      os.read(os.Path(res.fold(e => throw new Exception(e.describe), identity)))

    def assertCheckedAt(instant: Instant): Unit = {
      val actual = checkedFile.lastModified()
      if (actual != instant.toEpochMilli)
        sys.error(
          s"Expected .checked stamped at $instant, got ${Instant.ofEpochMilli(actual)}"
        )
    }
  }

  private def withSetup[T](ttl: Option[Duration])(f: Ctx => T): T = {
    val log   = new RequestLog
    val state = new ServerState
    withHttpServer(logRequests(log)(routes(state))) { serverUri =>
      withTmpDir { dir =>
        val base = FileCache[Task]((dir / "cache").toIO)
          .withChecksums(Nil)
          .withTtl(ttl)
        f(new Ctx(state, log, base, (serverUri / "dir" / "foo.pom").renderString))
      }
    }
  }

  val tests = Tests {

    test("a download creates a .checked file, stamped with the clock") {
      withSetup(Some(oneDay)) { ctx =>
        ctx.seedAt(t0)
        assert(ctx.checkedFile.exists())
        assert(ctx.checkedFile.length() == 0L)
        ctx.assertCheckedAt(t0)
      }
    }

    test("an update check that finds nothing newer refreshes .checked") {
      withSetup(Some(oneDay)) { ctx =>
        ctx.seedAt(t0)

        // TTL expired: the remote is checked, and has nothing newer, so no GET follows
        val (requests, res) = ctx.at(hoursIn(25), CachePolicy.LocalUpdateChanging)
        assert(requests == List("HEAD"))
        assert(ctx.contentOf(res) == firstContent)
        ctx.assertCheckedAt(hoursIn(25))

        // and that refreshed stamp is what drives the next decision: an hour later, the TTL has
        // not expired again, so nothing is checked
        val (requests0, res0) = ctx.at(hoursIn(26), CachePolicy.LocalUpdateChanging)
        assert(requests0 == Nil)
        assert(ctx.contentOf(res0) == firstContent)
      }
    }

    test("a download triggered by an update check refreshes .checked") {
      withSetup(Some(oneDay)) { ctx =>
        ctx.seedAt(t0)
        ctx.state.serveNewer()

        val (requests, res) = ctx.at(hoursIn(25), CachePolicy.LocalUpdateChanging)
        assert(requests == List("HEAD", "GET"))
        assert(ctx.contentOf(res) == secondContent)
        ctx.assertCheckedAt(hoursIn(25))

        val (requests0, _) = ctx.at(hoursIn(26), CachePolicy.LocalUpdateChanging)
        assert(requests0 == Nil)
      }
    }

    test("a missing .checked file forces a check") {
      withSetup(Some(oneDay)) { ctx =>
        ctx.seedAt(t0)
        assert(ctx.checkedFile.delete())

        // no record of a previous check, so one is due even though no time has passed
        val (requests, _) = ctx.at(t0, CachePolicy.LocalUpdateChanging)
        assert(requests == List("HEAD"))
        ctx.assertCheckedAt(t0)
      }
    }

    test("an infinite TTL never re-checks") {
      withSetup(Some(Duration.Inf)) { ctx =>
        ctx.seedAt(t0)
        ctx.state.serveNewer()

        val (requests, res) = ctx.at(hoursIn(24L * 365L * 10L), CachePolicy.LocalUpdateChanging)
        assert(requests == Nil)
        assert(ctx.contentOf(res) == firstContent)
      }
    }

    test("a zero TTL re-checks as soon as the clock moves") {
      withSetup(Some(Duration.Zero)) { ctx =>
        ctx.seedAt(t0)

        // the comparison is strict, so at the very instant of the last check nothing is due
        val (requests, _) = ctx.at(t0, CachePolicy.LocalUpdateChanging)
        assert(requests == Nil)

        // a second later, and every time after that
        val (requests0, _) = ctx.at(secondsIn(1), CachePolicy.LocalUpdateChanging)
        assert(requests0 == List("HEAD"))
        val (requests1, _) = ctx.at(secondsIn(2), CachePolicy.LocalUpdateChanging)
        assert(requests1 == List("HEAD"))
      }
    }

    test("no TTL at all re-checks every time") {
      withSetup(None) { ctx =>
        ctx.seedAt(t0)

        // unlike a zero TTL, the `.checked` stamp isn't consulted at all
        val (requests, _) = ctx.at(t0, CachePolicy.LocalUpdateChanging)
        assert(requests == List("HEAD"))
        val (requests0, _) = ctx.at(t0, CachePolicy.LocalUpdateChanging)
        assert(requests0 == List("HEAD"))
      }
    }
  }
}
