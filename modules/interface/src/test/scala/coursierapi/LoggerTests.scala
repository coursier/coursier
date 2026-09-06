package coursierapi

import coursier.cache.loggers.RefreshLogger
import coursier.internal.api.ApiHelper
import utest._

import java.io.{PrintWriter, Writer}

object LoggerTests extends TestSuite {

  private final class RecordingWriter extends Writer {
    private val b               = new StringBuilder
    @volatile private var used0 = false
    def used: Boolean           = used0
    def content: String         = b.synchronized(b.toString)
    def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
      used0 = true
      b.synchronized(b.appendAll(cbuf, off, len))
    }
    def flush(): Unit = used0 = true
    def close(): Unit = ()
  }

  private def cacheLogger(logger: Logger) =
    ApiHelper.cache(Cache.create().withLogger(logger)).logger

  val tests = Tests {

    test("progressBars") {

      test("acceptsAnyWriter") {
        // PrintWriter isn't an OutputStreamWriter, see
        // https://github.com/coursier/interface/issues/70
        val logger = Logger.progressBars(new PrintWriter(new RecordingWriter))
        assert(cacheLogger(logger).isInstanceOf[RefreshLogger])
      }

      test("writesToThePassedWriter") {
        val writer = new RecordingWriter
        val logger = cacheLogger(Logger.progressBars(new PrintWriter(writer)))
        val url    = "https://example.com/foo.jar"
        logger.init()
        try {
          logger.downloadingArtifact(url, coursier.util.Artifact(url))
          logger.downloadedArtifact(url, success = true)
        }
        finally logger.stop()

        assert(writer.used)
        if (logger.asInstanceOf[RefreshLogger].fallbackMode)
          assert(writer.content.contains(url))
      }
    }
  }
}
