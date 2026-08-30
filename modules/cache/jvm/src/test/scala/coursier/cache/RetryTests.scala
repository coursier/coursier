package coursier.cache

import coursier.cache.TestUtil._
import coursier.cache.protocol.TestretryHandler
import coursier.core.Authentication
import coursier.util.{Artifact, Task}
import utest._

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import javax.net.ssl.SSLException

import scala.concurrent.duration._

object RetryTests extends TestSuite {

  // The testretry:// protocol is served by coursier.cache.protocol.TestretryHandler,
  // which CacheUrl discovers by classpath convention (protocol.capitalize + "Handler")
  private def artifact = Artifact("testretry://fake.host/test/file.txt")

  private def retryCount = 6

  private def fileCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO)
      .withRetryBackoffInitialDelay(0.millis)
      .withChecksums(Seq(None))
      .withRetry(retryCount)

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
        assert(TestretryHandler.attempts.get() == retryCount)
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
      TestretryHandler.responseHeaders = Map("Retry-After" -> "0")

      withTmpDir { dir =>
        // Optional credentials: a 4xx used to be read as a hint that the request is worth
        // re-sending, non-optionally, straight away - which is precisely what a 429 is not.
        val artifact0 = artifact.withAuthentication(
          Some(Authentication("user", "pass").withOptional(true))
        )
        val result = get(fileCache(dir), artifact0)
        assert(result.isLeft)
        // one round-trip per attempt, and no extra "same request, now with credentials" on top
        assert(TestretryHandler.connections.get() == retryCount)
      }
    }

    test("don't download the file again when the update check is rate limited") {
      TestretryHandler.reset()
      TestretryHandler.responseCode = 429
      TestretryHandler.responseHeaders = Map("Retry-After" -> "0")

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
        assert(TestretryHandler.connections.get() == retryCount)
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
