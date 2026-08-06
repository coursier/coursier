package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import utest._

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._

/** Behavioural baseline for checksum validation and the corrupt-file retry loop.
  *
  * `DigestBasedCacheTests` covers computing digests; what is pinned here is what `FileCache` does
  * *around* a mismatch - which files it removes, whether it downloads again, and how many times.
  * The request counts are the point: a retry loop that silently gives up, or one that re-validates
  * a stale cached digest, both end up with the same error type.
  */
object FileCacheChecksumTests extends TestSuite {

  private val goodContent = "the artifact content"
  private val goodSum     = sha1(goodContent.getBytes("UTF-8"))

  /** A well-formed SHA-1, just not the one of `goodContent`. */
  private val badSum = sha1("something else entirely".getBytes("UTF-8"))

  private def asBigInteger(sum: String) = new java.math.BigInteger(sum, 16)

  /** Records the corrupt files the cache reported removing. */
  private final class RecordingLogger extends CacheLogger {
    private val removed = new ConcurrentLinkedQueue[String]
    override def removedCorruptFile(url: String, reason: Option[String]): Unit = {
      removed.add(url)
      ()
    }
    def removedUrls: List[String] = removed.iterator().asScala.toList
  }

  /** How the server answers `/dir/foo.jar.sha1`, and what it puts in `X-Checksum-SHA1`. */
  private final class ServerState {

    /** Number of checksums served so far, as a file or as a header. */
    val sumRequests = new AtomicInteger

    /** Sums to serve, one per request; the last one is repeated once exhausted. */
    @volatile var sums: Seq[String] = Seq(goodSum)

    /** Serve the checksum as an `X-Checksum-SHA1` header on the artifact rather than as a file. */
    @volatile var asHeader: Boolean = false

    /** Answer 404 on the checksum file. */
    @volatile var sumFound: Boolean = true

    def nextSum(): String = {
      val idx = sumRequests.getAndIncrement()
      sums(math.min(idx, sums.length - 1))
    }
  }

  private def routes(state: ServerState): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.jar" =>
        if (state.asHeader)
          Ok(goodContent).map(_.putHeaders("X-Checksum-SHA1" -> state.nextSum()))
        else
          Ok(goodContent)
      case GET -> Root / "dir" / "foo.jar.sha1" if state.sumFound =>
        Ok(state.nextSum())
    }

  private final class Ctx(
    val state: ServerState,
    val log: RequestLog,
    val logger: RecordingLogger,
    val cache: FileCache[Task],
    val url: String
  ) {

    def artifact: Artifact =
      Artifact(url).withChecksumUrls(Map("SHA-1" -> s"$url.sha1"))

    def run(artifact: Artifact = artifact): Either[ArtifactError, File] =
      cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)

    def withCache(f: FileCache[Task] => FileCache[Task]): Ctx =
      new Ctx(state, log, logger, f(cache), url)

    /** Names of the files sitting next to the artifact in cache. */
    def layout: Seq[String] = {
      val dir = os.Path(cache.localFile(url).getParentFile)
      if (os.exists(dir)) os.list(dir).filter(os.isFile).map(_.last).sorted
      else Nil
    }

    def auxiliaryFile(key: String): File =
      new File(cache.localFile(url).getParentFile, s".foo.jar__$key")

    /** How many times a given path was fetched. */
    def gets(path: String): Int =
      log.methodsAndPaths.count(_ == ("GET", path))

    def jarGets: Int = gets("/dir/foo.jar")
    def sumGets: Int = gets("/dir/foo.jar.sha1")
  }

  private def withSetup[T](f: Ctx => T): T = {
    val log    = new RequestLog
    val logger = new RecordingLogger
    val state  = new ServerState
    withHttpServer(logRequests(log)(routes(state))) { serverUri =>
      withTmpDir { dir =>
        val cache = FileCache[Task]((dir / "cache").toIO)
          .withChecksums(Seq(Some("SHA-1")))
          .withCachePolicies(Seq(CachePolicy.FetchMissing))
          .withLogger(logger)
        f(new Ctx(state, log, logger, cache, (serverUri / "dir" / "foo.jar").renderString))
      }
    }
  }

  val tests = Tests {

    test("a wrong checksum is retried after the corrupt file is removed") {
      withSetup { ctx0 =>

        val ctx = ctx0.withCache(_.withRetry(2))
        // first attempt gets a checksum that doesn't match, later ones the real one
        ctx.state.sums = Seq(badSum, goodSum)

        val res = ctx.run()
        val f   = res.fold(e => throw new Exception(e.describe), identity)
        assert(os.read(os.Path(f)) == goodContent)

        // the artifact and its checksum were fetched twice: the first pair was thrown away
        assert(ctx.jarGets == 2)
        assert(ctx.sumGets == 2)
        assert(ctx.logger.removedUrls == List(ctx.url))

        assert(ctx.layout == Seq(
          ".foo.jar.checked",
          ".foo.jar.sha1.checked",
          ".foo.jar__sha1.computed",
          "foo.jar",
          "foo.jar.sha1"
        ))
      }
    }

    test("the cached digest of a corrupt file doesn't survive the retry") {
      withSetup { ctx0 =>
        // If `.foo.jar__sha1.computed` were left behind, the second attempt would validate the
        // freshly downloaded artifact against the digest of the one that was thrown away.
        val ctx = ctx0.withCache(_.withRetry(2))
        ctx.state.sums = Seq(badSum, goodSum)

        assert(ctx.run().isRight)

        val computed = os.read.bytes(os.Path(ctx.auxiliaryFile("sha1.computed")))
        val asHex    = new java.math.BigInteger(1, computed).toString(16)
        assert(asHex == goodSum)
      }
    }

    test("a wrong checksum surfaces once the retries are exhausted") {

      def check(retry: Int, expectedAttempts: Int, expectedRemovals: Int): Unit =
        withSetup { ctx0 =>

          val ctx = ctx0.withCache(_.withRetry(retry))
          ctx.state.sums = Seq(badSum)

          ctx.run() match {
            case Left(err: ArtifactError.WrongChecksum) =>
              assert(err.sumType == "SHA-1")
              assert(asBigInteger(err.expected) == asBigInteger(badSum))
              assert(asBigInteger(err.got) == asBigInteger(goodSum))
            case other => sys.error(s"Expected a wrong checksum error, got $other")
          }

          if (ctx.jarGets != expectedAttempts)
            sys.error(s"Expected $expectedAttempts attempts, got ${ctx.log.methodsAndPaths}")
          assert(ctx.logger.removedUrls.length == expectedRemovals)

          // the last attempt removed nothing, so the corrupt pair is still there
          assert(ctx.layout.contains("foo.jar"))
          assert(ctx.layout.contains("foo.jar.sha1"))
        }

      test("no retry at all") {
        check(retry = 0, expectedAttempts = 1, expectedRemovals = 0)
      }
      test("one retry") {
        check(retry = 1, expectedAttempts = 2, expectedRemovals = 1)
      }
      test("three retries") {
        check(retry = 3, expectedAttempts = 4, expectedRemovals = 3)
      }
    }

    test("a checksum served as a header") {

      test("is used, without fetching the checksum file") {
        withSetup { ctx =>
          ctx.state.asHeader = true

          val res = ctx.run()
          val f   = res.fold(e => throw new Exception(e.describe), identity)
          assert(os.read(os.Path(f)) == goodContent)

          assert(ctx.jarGets == 1)
          assert(ctx.sumGets == 0)

          assert(ctx.layout == Seq(
            ".foo.jar.checked",
            ".foo.jar__sha1",
            ".foo.jar__sha1.computed",
            "foo.jar"
          ))
          assert(os.read(os.Path(ctx.auxiliaryFile("sha1"))) == goodSum)
        }
      }

      test("is cleared along with the corrupt file when it doesn't match") {
        withSetup { ctx0 =>

          val ctx = ctx0.withCache(_.withRetry(2))
          ctx.state.asHeader = true
          ctx.state.sums = Seq(badSum, goodSum)

          assert(ctx.run().isRight)

          assert(ctx.jarGets == 2)
          assert(ctx.sumGets == 0)
          assert(ctx.logger.removedUrls == List(ctx.url))
          // the header checksum from the first attempt was cleared, not appended to or kept
          assert(os.read(os.Path(ctx.auxiliaryFile("sha1"))) == goodSum)
        }
      }
    }

    test("a checksum missing from the server") {

      test("fails, and is not retried") {

        // The retry loop in `filePerPolicy0` only covers `WrongChecksum` and `ChecksumNotFound`.
        // A checksum file that fails to download never reaches either: `download` reports it as a
        // `ChecksumErrors`, which is returned as is.

        withSetup { ctx0 =>

          val ctx = ctx0.withCache(_.withRetry(3))
          ctx.state.sumFound = false

          ctx.run() match {
            case Left(err: ArtifactError.ChecksumErrors) =>
              assert(err.errors.map(_._1) == Seq("SHA-1"))
            case other => sys.error(s"Expected checksum errors, got $other")
          }

          assert(ctx.jarGets == 1)
          assert(ctx.logger.removedUrls.isEmpty)
        }
      }

      test("is tolerated when the checksum is optional") {
        withSetup { ctx0 =>
          // the `None` entry is what makes SHA-1 a best-effort check
          val ctx = ctx0.withCache(_.withChecksums(Seq(Some("SHA-1"), None)))
          ctx.state.sumFound = false

          val res = ctx.run()
          val f   = res.fold(e => throw new Exception(e.describe), identity)
          assert(os.read(os.Path(f)) == goodContent)
          // no digest was computed, so no `.computed` side file
          assert(ctx.layout == Seq(".foo.jar.checked", "foo.jar"))
        }
      }
    }

    test("a malformed checksum file fails without a retry") {
      withSetup { ctx0 =>

        val ctx = ctx0.withCache(_.withRetry(3))
        ctx.state.sums = Seq("not a checksum")

        ctx.run() match {
          case Left(err: ArtifactError.ChecksumFormatError) =>
            assert(err.sumType == "SHA-1")
          case other => sys.error(s"Expected a checksum format error, got $other")
        }

        assert(ctx.jarGets == 1)
        assert(ctx.logger.removedUrls.isEmpty)
      }
    }

    test("validateChecksum reports a missing local checksum file") {

      // `ChecksumNotFound` is what `validateChecksum` answers when no sum file - neither a
      // downloaded one nor a header one - can be found next to the artifact. The `retry` branch it
      // has in `filePerPolicy0` is currently unreachable through `file()`: `download` turns a
      // checksum that couldn't be fetched into a `ChecksumErrors` before validation is ever
      // reached. Pinned through the public `validateChecksum` instead, so the error itself stays
      // covered.

      withSetup { ctx0 =>

        val ctx = ctx0.withCache(_.withChecksums(Seq(Some("SHA-1"), None)))
        ctx.state.sumFound = false

        assert(ctx.run().isRight)
        assert(!ctx.auxiliaryFile("sha1").exists())

        val res = ctx.cache
          .validateChecksum(ctx.artifact, "SHA-1").run
          .unsafeRun(wrapExceptions = true)(ctx.cache.ec)

        res match {
          case Left(err: ArtifactError.ChecksumNotFound) =>
            assert(err.sumType == "SHA-1")
            assert(err.file == ctx.cache.localFile(ctx.url).getPath)
          case other => sys.error(s"Expected a checksum-not-found error, got $other")
        }
      }
    }
  }
}
