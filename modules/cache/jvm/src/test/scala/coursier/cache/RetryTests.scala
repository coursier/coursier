package coursier.cache

import coursier.cache.TestUtil._
import coursier.cache.internal.HostThrottle
import coursier.cache.protocol.TestretryHandler
import coursier.core.Authentication
import coursier.util.{Artifact, Task}
import utest._

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.SSLException

import scala.concurrent.duration._

object RetryTests extends TestSuite {

  // The testretry:// protocol is served by coursier.cache.protocol.TestretryHandler,
  // which CacheUrl discovers by classpath convention (protocol.capitalize + "Handler")
  private def artifact = Artifact("testretry://fake.host/test/file.txt")

  private def retryCount = 6

  // The pause a rate-limited host is held off for, scaled down so that the tests run in
  // milliseconds rather than the seconds the defaults are - deliberately - built around.
  private def throttle = new HostThrottle(
    initialDelay = 5.millis,
    maxDelay = Some(10.millis),
    // pinned rather than left to the default, which is a minute under CI and 5 seconds elsewhere
    maxRetryAfter = Some(5.seconds)
  )

  private def fileCache(
    dir: os.Path,
    // A wall clock budget, spent on the requests as much as on the pauses between them, so the
    // tests that mean to get through several rate limits ask for a generous one: how long a slow
    // machine takes to round-trip ten of them is not what they are checking. The default is short
    // for the ones that check we stop asking - they need the budget to actually run out.
    maxThrottleWait: FiniteDuration = 500.millis
  ): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO)
      .withRetryBackoffInitialDelay(0.millis)
      .withChecksums(Seq(None))
      .withRetry(retryCount)
      // its own, so that a pause one test records doesn't hold up the next one
      .withHostThrottle(throttle)
      .withMaxThrottleWait(Some(maxThrottleWait))

  private def get(dir: os.Path): Either[ArtifactError, File] =
    get(fileCache(dir), artifact)

  private def get(cache: FileCache[Task], artifact: Artifact): Either[ArtifactError, File] =
    cache.file(artifact).run
      .unsafeRun(wrapExceptions = false)(cache.ec)

  val tests = Tests {

    test("retry on SocketException") {
      val failCount = retryCount - 2
      assert(failCount > 2)
      TestretryHandler.reset(failUntil = failCount)

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isRight)
        assert(TestretryHandler.attempts.get() == failCount + 1)
      }
    }

    test("throw the actual SocketException at some point") {
      TestretryHandler.reset()

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isLeft)
        assert(TestretryHandler.attempts.get() == retryCount)
        result match {
          case Left(e: ArtifactError.DownloadError) =>
            assert(e.getMessage.contains("SocketException"))
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
      }
    }

    test("retry on SSLException") {
      val failCount = retryCount - 2
      assert(failCount > 2)
      TestretryHandler.reset(failUntil = failCount)
      TestretryHandler.createException = _ => new SSLException("foo SSLException")

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isRight)
        assert(TestretryHandler.attempts.get() == failCount + 1)
      }
    }

    // Is that case really necessary?
    test("retry on HTTP 429 IOException") {
      val failCount = retryCount - 2
      assert(failCount > 2)
      TestretryHandler.reset(failUntil = failCount)
      TestretryHandler.createException =
        _ =>
          new java.io.IOException(
            "Server returned HTTP response code: 429 for URL: https://example.com/file.txt"
          )

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isRight)
        assert(TestretryHandler.attempts.get() == failCount + 1)
      }
    }

    test("read Retry-After when retrying on HTTP 429") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      TestretryHandler.responseHeaders = Map("Retry-After" -> "0")

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isLeft)
        // how many attempts it gets through is bounded by the throttle budget rather than by the
        // failure count now, but it does keep trying - a server saying "come back in 0s" is one
        // asking to be retried, not one to give up on
        assert(TestretryHandler.attempts.get() > 1)
        result match {
          case Left(e: ArtifactError.RetryableHttpError) =>
            assert(e.responseCode == 429)
            assert(e.retryAfterOpt.contains(0.seconds))
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
      }
    }

    test("throw the actual SSLException at some point") {
      TestretryHandler.reset()
      TestretryHandler.createException = _ => new SSLException("foo SSLException")

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isLeft)
        assert(TestretryHandler.attempts.get() == retryCount)
        result match {
          case Left(e: ArtifactError.DownloadError) =>
            assert(e.getMessage.contains("foo SSLException"))
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
      }
    }

    test("don't re-send the request with credentials when rate limited") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      // longer than we are willing to wait, so this ends after a single round-trip
      TestretryHandler.responseHeaders = Map("Retry-After" -> "3600")

      withTmpDir { dir =>
        // Optional credentials: a 4xx used to be read as a hint that the request is worth
        // re-sending, non-optionally, straight away - which is precisely what a 429 is not.
        val artifact0 = artifact.withAuthentication(
          Some(Authentication("user", "pass").withOptional(true))
        )
        val result = get(fileCache(dir), artifact0)
        assert(result.isLeft)
        // the one request, and no extra "same request, now with credentials" on top of it
        assert(TestretryHandler.connections.get() == 1)
      }
    }

    test("don't download the file again when the update check is rate limited") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      // longer than we are willing to wait, so this ends after a single round-trip
      TestretryHandler.responseHeaders = Map("Retry-After" -> "3600")

      withTmpDir { dir =>
        val cache = fileCache(dir)
          .withCachePolicies(Seq(CachePolicy.Update))
          .withTtl(1.hour)

        // a cached file, with no ".checked" file alongside it, so the TTL check has to ask
        // the server whether it is still current - with a HEAD request
        val file = cache.localFile(artifact.url)
        file.getParentFile.mkdirs()
        Files.write(file.toPath, "cached-content".getBytes(UTF_8))

        val result = get(cache, artifact)
        result match {
          case Left(e: ArtifactError.RetryableHttpError) =>
            assert(e.responseCode == 429)
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
        // the rate-limited HEAD must not be read as "no last modified time, so download it again"
        assert(TestretryHandler.attempts.get() == 0)
        assert(TestretryHandler.connections.get() == 1)
      }
    }

    test("hold off the other downloads from a host that asked us to slow down") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      TestretryHandler.responseHeaders = Map("Retry-After" -> "3600")

      withTmpDir { dir =>
        val cache = fileCache(dir)
        val other = Artifact("testretry://fake.host/test/other.txt")

        val first  = get(cache, artifact)
        val second = get(cache, other)

        // the pause belongs to the host, not to the artifact that ran into it, so the second
        // download waits it out on the strength of the first one's 429 - without sending a
        // request of its own for the server to reject
        assert(TestretryHandler.connections.get() == 1)
        for (result <- Seq(first, second))
          result match {
            case Left(e: ArtifactError.RetryableHttpError) =>
              assert(e.responseCode == 429)
            case other0 =>
              throw new Exception(s"Unexpected result: $other0", other0.left.toOption.orNull)
          }
      }
    }

    test("give up rather than keep asking when Retry-After is longer than we will wait") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      TestretryHandler.responseHeaders = Map("Retry-After" -> "3600")

      withTmpDir { dir =>
        val result = get(dir)
        result match {
          case Left(e: ArtifactError.RetryableHttpError) =>
            assert(e.responseCode == 429)
            assert(e.retryAfterOpt.contains(3600.seconds))
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
        // it used to truncate the hour down to the cap and ask again at that pace, which is
        // both ignoring what the server asked for and, to it, more of what got us rate limited
        assert(TestretryHandler.connections.get() == 1)
      }
    }

    test("wait out more rate limits than the failure budget would allow") {
      // never fail the download itself, only rate limit it - for more attempts than `retryCount`,
      // which under a budget shared with actual failures could only ever end in giving up
      TestretryHandler.reset(failUntil = 0)
      TestretryHandler.rateLimitUntilConnection = retryCount + 3

      withTmpDir { dir =>
        val result = get(fileCache(dir, maxThrottleWait = 30.seconds), artifact)
        assert(result.isRight)
        // every rate-limited attempt, and the one that finally got through
        assert(TestretryHandler.connections.get() == retryCount + 4)
      }
    }

    test("report a host asking us to slow down, once rather than once per download") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      TestretryHandler.responseHeaders = Map("Retry-After" -> "3600")

      withTmpDir { dir =>
        val reported = new ConcurrentLinkedQueue[(String, FiniteDuration)]
        val logger = new CacheLogger {
          override def rateLimited(url: String, duration: FiniteDuration): Unit =
            reported.add((url, duration))
        }
        val cache = fileCache(dir).withLogger(logger)
        val other = Artifact("testretry://fake.host/test/other.txt")

        assert(get(cache, artifact).isLeft)
        assert(get(cache, other).isLeft)

        // the second download never sent a request of its own, so it has nothing of its own to
        // report - what is worth saying is that the host asked us to slow down, once
        assert(reported.size() == 1)
        val entry = reported.poll()
        assert(entry._1 == artifact.url)
        assert(entry._2 == 3600.seconds)
      }
    }

    test("stop if server keeps returning 5xx") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 501

      withTmpDir { dir =>
        val result = get(dir)
        assert(result.isLeft)
        assert(TestretryHandler.attempts.get() == retryCount)
        result match {
          case Left(e: ArtifactError.InternalServerError) =>
          case other =>
            throw new Exception(s"Unexpected result: $other", other.left.toOption.orNull)
        }
      }
    }
  }
}
