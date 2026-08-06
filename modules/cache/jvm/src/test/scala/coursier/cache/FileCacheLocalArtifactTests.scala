package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

import java.io.File
import java.time.{Clock, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

/** Behavioural baseline for `localArtifactsShouldBeCached`, which decides whether a `file:` URL is
  * read where it lies or copied into the cache first.
  *
  * The flag is threaded through `CachePath.localFile` and short-circuits `Downloader.downloadUrl`,
  * so it changes both the path callers get back and the side files that end up on disk.
  */
object FileCacheLocalArtifactTests extends TestSuite {

  private val zone = ZoneOffset.UTC
  private val t0   = LocalDateTime.of(2024, 1, 1, 12, 0, 0).toInstant(zone)

  private val content = "the local artifact"

  private final class Ctx(val cacheDir: os.Path, val srcDir: os.Path, val cached: Boolean) {

    val cache: FileCache[Task] =
      FileCache[Task](cacheDir.toIO)
        .withChecksums(Nil)
        .withCachePolicies(Seq(CachePolicy.FetchMissing))
        .withLocalArtifactsShouldBeCached(cached)
        .withTtl(Duration(24L, TimeUnit.HOURS))
        .withClock(Clock.fixed(t0, zone))

    val src: os.Path = srcDir / "foo.jar"
    val url: String  = fileUrl(src)

    def run(
      cache0: FileCache[Task] = cache,
      artifact: Artifact = Artifact(url)
    ): Either[ArtifactError, File] =
      cache0.file(artifact).run.unsafeRun(wrapExceptions = true)(cache0.ec)

    /** Everything under the cache directory, as paths relative to it. */
    def cacheLayout: Seq[os.SubPath] =
      if (os.exists(cacheDir))
        os.walk(cacheDir).filter(os.isFile).map(_.relativeTo(cacheDir).asSubPath).sorted
      else
        Nil

    /** Everything sitting next to the source artifact. */
    def srcLayout: Seq[String] =
      os.list(srcDir).map(_.last).sorted
  }

  private def withSetup[T](cached: Boolean, create: Boolean = true)(f: Ctx => T): T =
    withTmpDir { dir =>
      val ctx = new Ctx(dir / "cache", dir / "src", cached)
      os.makeDir.all(ctx.srcDir)
      if (create)
        os.write(ctx.src, content)
      f(ctx)
    }

  val tests = Tests {

    test("read in place") {

      test("the artifact is served from where it is, and nothing is cached") {
        withSetup(cached = false) { ctx =>

          val res = ctx.run()
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(os.Path(f) == ctx.src)
          assert(os.read(os.Path(f)) == content)

          // nothing at all lands in the cache - not even a .checked file
          assert(ctx.cacheLayout.isEmpty)
          // … and no lock or part file is left next to the source either
          assert(ctx.srcLayout == Seq("foo.jar"))
        }
      }

      test("localFile points at the source file") {
        withSetup(cached = false) { ctx =>
          assert(os.Path(ctx.cache.localFile(ctx.url)) == ctx.src)
        }
      }

      test("changes to the source are picked up immediately") {
        withSetup(cached = false) { ctx =>
          assert(ctx.run().isRight)
          os.write.over(ctx.src, "something else")
          val f = ctx.run().fold(e => throw new Exception(e.describe), identity)
          assert(os.read(os.Path(f)) == "something else")
        }
      }

      test("a missing file is a not-found") {
        withSetup(cached = false, create = false) { ctx =>
          ctx.run() match {
            case Left(_: ArtifactError.NotFound) =>
            case other => sys.error(s"Expected a not-found error, got $other")
          }
          assert(ctx.cacheLayout.isEmpty)
        }
      }
    }

    test("copy into the cache") {

      test("the artifact is copied, with the usual side files") {
        withSetup(cached = true) { ctx =>

          val res = ctx.run()
          val f   = res.fold(e => throw new Exception(e.describe), identity)

          assert(os.Path(f) != ctx.src)
          assert(os.Path(f).startsWith(ctx.cacheDir))
          assert(os.read(os.Path(f)) == content)

          // the source is left alone…
          assert(ctx.srcLayout == Seq("foo.jar"))
          // … and the copy gets the same treatment as a downloaded artifact
          val layout = ctx.cacheLayout.map(_.last)
          assert(layout == Seq(".foo.jar.checked", "foo.jar"))
          assert(ctx.cacheLayout.head.segments.head == "file")
        }
      }

      test("localFile points into the cache") {
        withSetup(cached = true) { ctx =>
          val local = os.Path(ctx.cache.localFile(ctx.url))
          assert(local.startsWith(ctx.cacheDir))
          assert(local.last == "foo.jar")
        }
      }

      test("later changes to the source are not picked up") {
        withSetup(cached = true) { ctx =>
          assert(ctx.run().isRight)
          os.write.over(ctx.src, "something else")

          // FetchMissing is happy with what is in cache, so the copy goes stale
          val f = ctx.run().fold(e => throw new Exception(e.describe), identity)
          assert(os.read(os.Path(f)) == content)
        }
      }

      test("a missing file is a not-found") {
        withSetup(cached = true, create = false) { ctx =>
          ctx.run() match {
            case Left(_: ArtifactError.NotFound) =>
            case other => sys.error(s"Expected a not-found error, got $other")
          }
          // the failed download leaves nothing behind
          assert(ctx.cacheLayout.isEmpty)
        }
      }

      test("an update check on a cached local artifact fails") {

        // Current behaviour, pinned as a baseline rather than endorsed. Once the artifact is cached,
        // an update policy tries a HEAD request on the `file:` URL, and `Blocking.urlLastModified`
        // only knows how to do that over HTTP - anything else is reported as a download error. The
        // TTL is what makes this reachable: within it, no check is attempted at all.

        withSetup(cached = true) { ctx =>
          assert(ctx.run().isRight)

          val updating = ctx.cache.withCachePolicies(Seq(CachePolicy.Update))

          // within the TTL, the cached copy answers
          assert(ctx.run(updating).isRight)

          // past it, the check is attempted, and fails
          val later = updating.withClock(Clock.fixed(t0.plusSeconds(25L * 3600L), zone))
          ctx.run(later) match {
            case Left(err: ArtifactError.DownloadError) =>
              assert(err.describe.contains("Cannot do HEAD request"))
            case other => sys.error(s"Expected a download error, got $other")
          }
        }
      }
    }
  }
}
