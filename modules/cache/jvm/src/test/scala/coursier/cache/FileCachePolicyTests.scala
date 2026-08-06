package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.dsl.io._
import org.http4s.headers.`Last-Modified`
import org.http4s.{HttpDate, HttpRoutes}
import utest._

import java.time.{Clock, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

/** Behavioural baseline for the cache policy matrix.
  *
  * Each case pins the *requests that reach the server*, not only the resulting content: several of
  * these policies differ only in whether they check for updates, which is invisible to a
  * content-only assertion.
  *
  * The cases marked "downgraded" exercise `Downloader.actualCachePolicy`, which rewrites
  * `UpdateChanging` to `FetchMissing` and `LocalUpdateChanging` / `LocalOnlyIfValid` to `LocalOnly`
  * for non-changing artifacts without a `check` extra.
  */
object FileCachePolicyTests extends TestSuite {

  private val zone = ZoneOffset.UTC

  private val ttl = Duration(24L, TimeUnit.HOURS)

  /** The instant the artifact is seeded in the cache at. */
  private val seedTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0).toInstant(zone)

  /** Less than a TTL after `seedTime`: the cached file is still fresh. */
  private val freshTime = seedTime.plusSeconds(3600L)

  /** More than a TTL after `seedTime`: the cached file is stale. */
  private val staleTime = seedTime.plusSeconds(25L * 3600L)

  // These drive the "is the remote newer?" comparison. They are compared against the last modified
  // time of the file in cache, which coursier sets from the Last-Modified header of the response it
  // was downloaded from - so they are independent from the clock the cache runs with.
  private val lastModified1 =
    HttpDate.unsafeFromInstant(LocalDateTime.of(2023, 1, 1, 0, 0, 0).toInstant(zone))
  private val lastModified2 =
    HttpDate.unsafeFromInstant(LocalDateTime.of(2023, 6, 1, 0, 0, 0).toInstant(zone))

  private val firstContent  = "first pom"
  private val secondContent = "second pom"

  private final class ServerState {
    @volatile var content: String        = firstContent
    @volatile var lastModified: HttpDate = lastModified1
    @volatile var found: Boolean         = true
  }

  private def routes(state: ServerState): HttpRoutes[IO] = {
    def respond =
      Ok(state.content).map(_.putHeaders(`Last-Modified`(state.lastModified)))
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.pom" if state.found  => respond
      case HEAD -> Root / "dir" / "foo.pom" if state.found => respond
    }
  }

  /** State of the artifact in the cache when the policy under test runs. */
  private sealed abstract class Local extends Product with Serializable
  private object Local {

    /** Not in cache at all. */
    case object Absent extends Local

    /** In cache, last checked less than a TTL ago. */
    case object Fresh extends Local

    /** In cache, last checked more than a TTL ago. */
    case object Stale extends Local
  }

  /** State of the artifact on the server when the policy under test runs. */
  private sealed abstract class Remote extends Product with Serializable
  private object Remote {

    /** Same content, same Last-Modified as what was seeded. */
    case object Same extends Remote

    /** Different content, newer Last-Modified. */
    case object Newer extends Remote

    /** Gone from the server. */
    case object Missing extends Remote
  }

  private sealed abstract class Outcome extends Product with Serializable
  private object Outcome {
    final case class Content(value: String) extends Outcome
    case object NotFound                    extends Outcome
    case object TooOld                      extends Outcome
    case object Forbidden                   extends Outcome
  }

  /** Runs one row of the matrix.
    *
    * @param expectedRequests
    *   the HTTP methods, in order, that the server is expected to see once the artifact is in the
    *   expected local state
    */
  private def check(
    policy: CachePolicy,
    changing: Boolean,
    local: Local,
    remote: Remote,
    expectedRequests: Seq[String],
    expectedOutcome: Outcome
  ): Unit = {

    val log   = new RequestLog
    val state = new ServerState

    withHttpServer(logRequests(log)(routes(state))) { serverUri =>
      withTmpDir { dir =>

        val url = (serverUri / "dir" / "foo.pom").renderString

        val baseCache = FileCache[Task]((dir / "cache").toIO)
          .withTtl(ttl)
          // no checksums, so that the request log only contains requests for the artifact itself
          .withChecksums(Nil)

        def run(cache: FileCache[Task], artifact: Artifact) =
          cache
            .file(artifact).run
            .unsafeRun(wrapExceptions = true)(cache.ec)

        if (local != Local.Absent) {
          val seeded = run(
            baseCache
              .withCachePolicies(Seq(CachePolicy.FetchMissing))
              .withClock(Clock.fixed(seedTime, zone)),
            Artifact(url)
          )
          assert(seeded.isRight)
        }

        remote match {
          case Remote.Same => // leave the server as it was seeded from
          case Remote.Newer =>
            state.content = secondContent
            state.lastModified = lastModified2
          case Remote.Missing =>
            state.found = false
        }

        log.reset()

        val now = local match {
          case Local.Stale => staleTime
          case _           => freshTime
        }

        val res = run(
          baseCache
            .withCachePolicies(Seq(policy))
            .withClock(Clock.fixed(now, zone)),
          Artifact(url).withChanging(changing)
        )

        val actualRequests = log.methods
        if (actualRequests != expectedRequests.toList)
          sys.error(
            s"Expected requests ${expectedRequests.toList}, got $actualRequests (result: $res)"
          )

        expectedOutcome match {
          case Outcome.Content(expected) =>
            val f       = res.fold(e => throw new Exception(e.describe), identity)
            val content = os.read(os.Path(f))
            assert(content == expected)
          case Outcome.NotFound =>
            res match {
              case Left(_: ArtifactError.NotFound) =>
              case other => sys.error(s"Expected a not found error, got $other")
            }
          case Outcome.TooOld =>
            res match {
              case Left(_: ArtifactError.FileTooOldOrNotFound) =>
              case other => sys.error(s"Expected a file-too-old error, got $other")
            }
          case Outcome.Forbidden =>
            res match {
              case Left(_: ArtifactError.ForbiddenChangingArtifact) =>
              case other => sys.error(s"Expected a forbidden-changing error, got $other")
            }
        }
      }
    }
  }

  val tests = Tests {

    test("LocalOnly") {
      test("absent") {
        check(
          CachePolicy.LocalOnly,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.NotFound
        )
      }
      test("present") {
        // no update check, even though the remote has something newer
        check(
          CachePolicy.LocalOnly,
          changing = false,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("present and stale") {
        check(
          CachePolicy.LocalOnly,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
    }

    test("LocalOnlyIfValid") {
      test("changing, stale") {
        check(
          CachePolicy.LocalOnlyIfValid,
          changing = true,
          Local.Stale,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.TooOld
        )
      }
      test("changing, fresh") {
        check(
          CachePolicy.LocalOnlyIfValid,
          changing = true,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("non-changing, stale") {
        // downgraded to LocalOnly, so the TTL doesn't apply
        check(
          CachePolicy.LocalOnlyIfValid,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("changing, absent") {
        check(
          CachePolicy.LocalOnlyIfValid,
          changing = true,
          Local.Absent,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.NotFound
        )
      }
    }

    test("LocalUpdateChanging") {
      test("changing, stale, remote newer") {
        check(
          CachePolicy.LocalUpdateChanging,
          changing = true,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Seq("HEAD", "GET"),
          Outcome.Content(secondContent)
        )
      }
      test("changing, stale, remote unchanged") {
        // the HEAD is enough, no GET follows
        check(
          CachePolicy.LocalUpdateChanging,
          changing = true,
          Local.Stale,
          Remote.Same,
          expectedRequests = Seq("HEAD"),
          Outcome.Content(firstContent)
        )
      }
      test("changing, fresh") {
        check(
          CachePolicy.LocalUpdateChanging,
          changing = true,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("changing, absent") {
        // updates are only checked for files already in cache - nothing gets downloaded
        check(
          CachePolicy.LocalUpdateChanging,
          changing = true,
          Local.Absent,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.NotFound
        )
      }
      test("non-changing, stale") {
        // downgraded to LocalOnly
        check(
          CachePolicy.LocalUpdateChanging,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
    }

    test("LocalUpdate") {
      test("non-changing, stale, remote unchanged") {
        // unlike LocalUpdateChanging, non-changing artifacts are checked too
        check(
          CachePolicy.LocalUpdate,
          changing = false,
          Local.Stale,
          Remote.Same,
          expectedRequests = Seq("HEAD"),
          Outcome.Content(firstContent)
        )
      }
      test("non-changing, stale, remote newer") {
        check(
          CachePolicy.LocalUpdate,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Seq("HEAD", "GET"),
          Outcome.Content(secondContent)
        )
      }
      test("non-changing, fresh") {
        check(
          CachePolicy.LocalUpdate,
          changing = false,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("absent") {
        check(
          CachePolicy.LocalUpdate,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.NotFound
        )
      }
    }

    test("UpdateChanging") {
      test("non-changing, absent") {
        // downgraded to FetchMissing
        check(
          CachePolicy.UpdateChanging,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Seq("GET"),
          Outcome.Content(firstContent)
        )
      }
      test("non-changing, stale") {
        // downgraded to FetchMissing, so no update check at all
        check(
          CachePolicy.UpdateChanging,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("changing, stale, remote newer") {
        check(
          CachePolicy.UpdateChanging,
          changing = true,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Seq("HEAD", "GET"),
          Outcome.Content(secondContent)
        )
      }
      test("changing, absent") {
        check(
          CachePolicy.UpdateChanging,
          changing = true,
          Local.Absent,
          Remote.Same,
          expectedRequests = Seq("GET"),
          Outcome.Content(firstContent)
        )
      }
    }

    test("Update") {
      test("non-changing, stale, remote newer") {
        check(
          CachePolicy.Update,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Seq("HEAD", "GET"),
          Outcome.Content(secondContent)
        )
      }
      test("non-changing, stale, remote unchanged") {
        check(
          CachePolicy.Update,
          changing = false,
          Local.Stale,
          Remote.Same,
          expectedRequests = Seq("HEAD"),
          Outcome.Content(firstContent)
        )
      }
      test("non-changing, fresh") {
        check(
          CachePolicy.Update,
          changing = false,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("absent") {
        // nothing in cache, so no update check - straight to the GET
        check(
          CachePolicy.Update,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Seq("GET"),
          Outcome.Content(firstContent)
        )
      }
    }

    test("FetchMissing") {
      test("present and stale") {
        check(
          CachePolicy.FetchMissing,
          changing = false,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("changing and present") {
        check(
          CachePolicy.FetchMissing,
          changing = true,
          Local.Stale,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Content(firstContent)
        )
      }
      test("absent") {
        check(
          CachePolicy.FetchMissing,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Seq("GET"),
          Outcome.Content(firstContent)
        )
      }
      test("absent, missing on the server") {
        check(
          CachePolicy.FetchMissing,
          changing = false,
          Local.Absent,
          Remote.Missing,
          expectedRequests = Seq("GET"),
          Outcome.NotFound
        )
      }
    }

    test("ForceDownload") {
      test("present") {
        check(
          CachePolicy.ForceDownload,
          changing = false,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Seq("GET"),
          Outcome.Content(secondContent)
        )
      }
      test("absent") {
        check(
          CachePolicy.ForceDownload,
          changing = false,
          Local.Absent,
          Remote.Same,
          expectedRequests = Seq("GET"),
          Outcome.Content(firstContent)
        )
      }
    }

    test("ForbiddenChangingArtifact") {
      test("absent") {
        check(
          CachePolicy.NoChanging.FetchMissing,
          changing = true,
          Local.Absent,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.Forbidden
        )
      }
      test("present") {
        check(
          CachePolicy.NoChanging.LocalOnly,
          changing = true,
          Local.Fresh,
          Remote.Same,
          expectedRequests = Nil,
          Outcome.Forbidden
        )
      }
      test("force download") {
        check(
          CachePolicy.NoChanging.ForceDownload,
          changing = true,
          Local.Fresh,
          Remote.Newer,
          expectedRequests = Nil,
          Outcome.Forbidden
        )
      }
    }
  }
}
