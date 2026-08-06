package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import utest._

import java.io.File
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

/** Behavioural baseline for cached download errors - the `.error` side files.
  *
  * Only incidentally covered before, by one case in `FileCacheTests`. As everywhere else in these
  * suites, the assertions are on the requests the server sees: the whole point of an `.error` file
  * is to *not* issue a request, which is invisible to a content-only assertion.
  *
  * Two details of the current implementation shape most of what follows, and are pinned here rather
  * than endorsed:
  *   - the `.error` short-circuit lives in `Downloader.shouldDownload`, which the `FetchMissing`
  *     and `ForceDownload` branches of `downloadUrl` never call - so those two policies ignore
  *     cached errors entirely;
  *   - `.error` files are *written* with wall-clock time (`Files.write`) but *read back* against
  *     the cache's `Clock`, unlike `.checked` files which `doTouchCheckFile` stamps from the clock.
  *     The tests below stamp `.error` explicitly so that both sides agree.
  */
object FileCacheErrorTests extends TestSuite {

  private val zone = ZoneOffset.UTC

  private val ttl = Duration(24L, TimeUnit.HOURS)

  private val t0 = LocalDateTime.of(2024, 1, 1, 12, 0, 0).toInstant(zone)

  private def hoursIn(h: Long): Instant = t0.plusSeconds(h * 3600L)

  private val jarContent = "the artifact"
  private val pomContent = "the metadata"

  private final class ServerState {

    /** Whether `/dir/foo.jar` is on the server. `/dir/foo.pom` always is. */
    @volatile var found: Boolean = false
  }

  private def routes(state: ServerState): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.jar" if state.found  => Ok(jarContent)
      case HEAD -> Root / "dir" / "foo.jar" if state.found => Ok(jarContent)
      case GET -> Root / "dir" / "foo.pom"                 => Ok(pomContent)
      case HEAD -> Root / "dir" / "foo.pom"                => Ok(pomContent)
    }

  private final class Ctx(
    val state: ServerState,
    val log: RequestLog,
    val base: FileCache[Task],
    val jarUrl: String,
    val pomUrl: String
  ) {

    /** Runs one fetch at `instant`, and returns the requests it issued along with its result. */
    def at(
      instant: Instant,
      policy: CachePolicy,
      artifact: Artifact
    ): (List[String], Either[ArtifactError, File]) = {
      log.reset()
      val cache = base
        .withCachePolicies(Seq(policy))
        .withClock(Clock.fixed(instant, zone))
      val res = cache
        .file(artifact).run
        .unsafeRun(wrapExceptions = true)(cache.ec)
      (log.methods, res)
    }

    def jar: Artifact = Artifact(jarUrl)
    def pom: Artifact = Artifact(pomUrl)

    /** Puts `/dir/foo.pom` in cache, so that it can act as a reference file. */
    def seedPom(): Unit = {
      val (_, res) = at(t0, CachePolicy.FetchMissing, pom)
      assert(res.isRight)
    }

    def errorFile: File = {
      val f = base.localFile(jarUrl)
      new File(f.getParentFile, s".${f.getName}.error")
    }

    /** Aligns the `.error` file's timestamp with the clock the cache is driven by. */
    def stampErrorAt(instant: Instant): Unit = {
      assert(errorFile.exists())
      assert(errorFile.setLastModified(instant.toEpochMilli))
    }
  }

  private def withSetup[T](f: Ctx => T): T = {
    val log   = new RequestLog
    val state = new ServerState
    withHttpServer(logRequests(log)(routes(state))) { serverUri =>
      withTmpDir { dir =>
        val base = FileCache[Task]((dir / "cache").toIO)
          .withChecksums(Nil)
          .withTtl(ttl)
        f(new Ctx(
          state,
          log,
          base,
          (serverUri / "dir" / "foo.jar").renderString,
          (serverUri / "dir" / "foo.pom").renderString
        ))
      }
    }
  }

  private def assertNotFound(res: Either[ArtifactError, File]): ArtifactError.NotFound =
    res match {
      case Left(nf: ArtifactError.NotFound) => nf
      case other                            => sys.error(s"Expected a not-found error, got $other")
    }

  val tests = Tests {

    test("a permanent not-found is remembered in a .error file") {
      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)

        val (requests, res) = ctx.at(t0, CachePolicy.Update, changing)
        assert(requests == List("GET"))
        assert(assertNotFound(res).permanent.contains(true))
        assert(ctx.errorFile.exists())
        assert(ctx.errorFile.length() == 0L)

        ctx.stampErrorAt(t0)

        // within the TTL: the remembered error answers, without any request
        val (requests0, res0) = ctx.at(hoursIn(1), CachePolicy.Update, changing)
        assert(requests0 == Nil)
        // the cached error isn't reported as permanent - only the fresh one is
        assert(assertNotFound(res0).permanent.isEmpty)
      }
    }

    test("a cached error expires with the TTL") {
      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)

        assert(ctx.at(t0, CachePolicy.Update, changing)._1 == List("GET"))
        ctx.stampErrorAt(t0)

        // more than a TTL later, the error is not trusted any more and the server is asked again
        val (requests, res) = ctx.at(hoursIn(25), CachePolicy.Update, changing)
        assert(requests == List("GET"))
        assert(assertNotFound(res).permanent.contains(true))
        assert(ctx.errorFile.exists())
      }
    }

    test("an infinite TTL never expires a cached error") {
      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)
        val base     = ctx.base

        assert(ctx.at(t0, CachePolicy.Update, changing)._1 == List("GET"))
        ctx.stampErrorAt(t0)

        val infiniteTtl =
          new Ctx(ctx.state, ctx.log, base.withTtl(Duration.Inf), ctx.jarUrl, ctx.pomUrl)
        val (requests, res) =
          infiniteTtl.at(hoursIn(24L * 365L * 10L), CachePolicy.Update, changing)
        assert(requests == Nil)
        assertNotFound(res)
      }
    }

    test("a successful download deletes the cached error") {
      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)

        assert(ctx.at(t0, CachePolicy.Update, changing)._1 == List("GET"))
        ctx.stampErrorAt(t0)

        ctx.state.found = true

        // the TTL has to expire first, otherwise nothing is even attempted
        val (requests, res) = ctx.at(hoursIn(25), CachePolicy.Update, changing)
        assert(requests == List("GET"))
        val f = res.fold(e => throw new Exception(e.describe), identity)
        assert(os.read(os.Path(f)) == jarContent)
        assert(!ctx.errorFile.exists())
      }
    }

    test("errors are only cached when asked for") {

      test("a plain artifact leaves no .error behind") {
        withSetup { ctx =>
          val (requests, res) = ctx.at(t0, CachePolicy.Update, ctx.jar)
          assert(requests == List("GET"))
          assertNotFound(res)
          assert(!ctx.errorFile.exists())

          // … so the next fetch goes to the server again
          val (requests0, _) = ctx.at(hoursIn(1), CachePolicy.Update, ctx.jar)
          assert(requests0 == List("GET"))
        }
      }

      test("the cache-errors extra opts a non-changing artifact in") {
        withSetup { ctx =>
          val artifact = ctx.jar.withExtra(Map("cache-errors" -> ctx.jar))

          val (requests, _) = ctx.at(t0, CachePolicy.Update, artifact)
          assert(requests == List("GET"))
          assert(ctx.errorFile.exists())
          ctx.stampErrorAt(t0)

          val (requests0, res0) = ctx.at(hoursIn(1), CachePolicy.Update, artifact)
          assert(requests0 == Nil)
          assertNotFound(res0)
        }
      }
    }

    test("a metadata reference file") {

      test("makes a cached error permanent, TTL or not") {
        withSetup { ctx =>
          ctx.seedPom()

          // neither changing nor cache-errors: it is the reference file in cache that enables
          // remembering the error
          val artifact = ctx.jar.withExtra(Map("metadata" -> ctx.pom))

          val (requests, _) = ctx.at(t0, CachePolicy.Update, artifact)
          assert(requests == List("GET"))
          assert(ctx.errorFile.exists())
          ctx.stampErrorAt(t0)

          // long past the TTL, and still no request: this branch of `checkErrFile` doesn't consult
          // the timestamp at all
          val (requests0, res0) = ctx.at(hoursIn(24L * 365L), CachePolicy.Update, artifact)
          assert(requests0 == Nil)
          assert(assertNotFound(res0).permanent.contains(true))
        }
      }

      test("turns a transient download error into the cached not-found") {

        // The branch at Downloader.scala:641. It is only reachable under a policy that doesn't go
        // through `shouldDownload` - `FetchMissing` here - since `shouldDownload` would answer from
        // the `.error` file before any connection is attempted.

        val log   = new RequestLog
        val state = new ServerState

        withTmpDir { dir =>

          val base = FileCache[Task]((dir / "cache").toIO)
            .withChecksums(Nil)
            .withTtl(ttl)
            // a single attempt, so that the connection failure surfaces without waiting out the
            // retry backoff
            .withRetry(0)

          val (jarUrl, pomUrl) =
            withHttpServer(logRequests(log)(routes(state))) { serverUri =>
              val ctx = new Ctx(
                state,
                log,
                base,
                (serverUri / "dir" / "foo.jar").renderString,
                (serverUri / "dir" / "foo.pom").renderString
              )
              ctx.seedPom()
              val artifact      = ctx.jar.withExtra(Map("metadata" -> ctx.pom))
              val (requests, _) = ctx.at(t0, CachePolicy.FetchMissing, artifact)
              assert(requests == List("GET"))
              assert(ctx.errorFile.exists())
              (ctx.jarUrl, ctx.pomUrl)
            }

          // the server is gone now, so connecting to it fails with an IOException
          val ctx      = new Ctx(state, log, base, jarUrl, pomUrl)
          val artifact = ctx.jar.withExtra(Map("metadata" -> ctx.pom))

          val (_, res) = ctx.at(hoursIn(1), CachePolicy.FetchMissing, artifact)
          val nf       = assertNotFound(res)
          assert(nf.permanent.contains(true))
          // the download error is kept as the cause, rather than reported as the error
          assert(Option(nf.getCause).exists(_.isInstanceOf[ArtifactError.DownloadError]))
          // and the cached error survives the transient failure
          assert(ctx.errorFile.exists())
        }
      }
    }

    test("FetchMissing ignores cached errors") {

      // Pinned as a baseline rather than endorsed: `Downloader.downloadUrl`'s `FetchMissing` branch
      // goes straight from `checkFileExists` to `remoteKeepErrors`, never calling `shouldDownload`,
      // so the `.error` file it writes is only ever read back by the update policies.

      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)

        assert(ctx.at(t0, CachePolicy.FetchMissing, changing)._1 == List("GET"))
        assert(ctx.errorFile.exists())
        ctx.stampErrorAt(t0)

        val (requests, _) = ctx.at(hoursIn(1), CachePolicy.FetchMissing, changing)
        assert(requests == List("GET"))
      }
    }

    test("a cached error doesn't hide a file that is in cache") {
      withSetup { ctx =>

        val changing = ctx.jar.withChanging(true)

        ctx.state.found = true
        assert(ctx.at(t0, CachePolicy.FetchMissing, changing)._2.isRight)

        // an .error left over from some earlier failure, next to the artifact itself
        os.write(os.Path(ctx.errorFile), Array.emptyByteArray)
        ctx.stampErrorAt(t0)

        // LocalOnly doesn't consult it either - the file is served from cache
        val (requests, res) = ctx.at(hoursIn(1), CachePolicy.LocalOnly, changing)
        assert(requests == Nil)
        val f = res.fold(e => throw new Exception(e.describe), identity)
        assert(os.read(os.Path(f)) == jarContent)
      }
    }
  }
}
