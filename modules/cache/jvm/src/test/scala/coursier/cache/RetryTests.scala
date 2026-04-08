package coursier.cache

import coursier.cache.TestUtil._
import coursier.cache.protocol.TestretryHandler
import coursier.util.{Artifact, Task}
import utest._

import java.io.File
import javax.net.ssl.SSLException

import scala.concurrent.duration._

object RetryTests extends TestSuite {

  // The testretry:// protocol is served by coursier.cache.protocol.TestretryHandler,
  // which CacheUrl discovers by classpath convention (protocol.capitalize + "Handler")
  private def artifact = Artifact("testretry://fake.host/test/file.txt")

  private def retryCount = 6

  private def get(dir: os.Path): Either[ArtifactError, File] = {
    val cache = FileCache[Task]((dir / "cache").toIO)
      .withRetryBackoffInitialDelay(0.millis)
      .withChecksums(Seq(None))
      .withRetry(retryCount)
    cache.file(artifact).run
      .unsafeRun(wrapExceptions = false)(cache.ec)
  }

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
