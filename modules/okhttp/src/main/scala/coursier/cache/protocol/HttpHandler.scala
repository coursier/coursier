package coursier.cache.protocol

import java.net.{URLStreamHandler, URLStreamHandlerFactory}
import java.util.concurrent.TimeUnit

import com.squareup.okhttp.{OkHttpClient, OkUrlFactory}

import scala.concurrent.duration.FiniteDuration

/**
  * Contains the default values for the OK Http client.
  *
  * The default client can be configured using properties which are first checked from the system properties, then environment variables:
  *
  * - COURSIER_CONN_TIMEOUT_SECONDS : the http connection timeout, in seconds
  * - COURSIER_READ_TIMEOUT_SECONDS : the http read timeout, in seconds
  *
  */
object HttpHandler {

  private def durationInSecondsFromEnv(key : String): Option[FiniteDuration] = {
    import concurrent.duration._
    sys.props.get(key).orElse(sys.env.get(key)).map(_.toInt.seconds)
  }

  def newClient() = {
    val client = new OkHttpClient

    durationInSecondsFromEnv("COURSIER_CONN_TIMEOUT_SECONDS").foreach { timeoutSeconds =>
      client.setConnectTimeout(timeoutSeconds.toSeconds, TimeUnit.SECONDS)
    }

    durationInSecondsFromEnv("COURSIER_READ_TIMEOUT_SECONDS").foreach { timeoutSeconds =>
      client.setReadTimeout(timeoutSeconds.toSeconds, TimeUnit.SECONDS)
    }

    client
  }

  lazy val okHttpClient = newClient

  lazy val okHttpFactory: OkUrlFactory = new OkUrlFactory(okHttpClient)
}

class HttpHandler(okHttpFactory: OkUrlFactory = HttpHandler.okHttpFactory) extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = {
    okHttpFactory.createURLStreamHandler(protocol)
  }
}

class HttpsHandler(okHttpFactory: OkUrlFactory = HttpHandler.okHttpFactory) extends HttpHandler(okHttpFactory)
