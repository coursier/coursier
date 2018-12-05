package coursier.cache

import java.io.File

import coursier.paths.CachePath
import coursier.util.Schedulable

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object CacheDefaults {

  lazy val location: File = CachePath.defaultCacheDirectory()

  val concurrentDownloadCount = 6

  lazy val pool = Schedulable.fixedThreadPool(concurrentDownloadCount)

  lazy val ttl: Option[Duration] = {
    def fromString(s: String) =
      Try(Duration(s)).toOption

    val fromEnv = sys.env.get("COURSIER_TTL").flatMap(fromString)
    def fromProps = sys.props.get("coursier.ttl").flatMap(fromString)
    def default = 24.hours

    fromEnv
      .orElse(fromProps)
      .orElse(Some(default))
  }

  // Check SHA-1 if available, else be fine with no checksum
  val checksums = Seq(Some("SHA-1"), None)

  private def defaultSslRetryCount = 3

  lazy val sslRetryCount =
    sys.props
      .get("coursier.sslexception-retry")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(_ >= 0)
      .getOrElse(defaultSslRetryCount)

  val bufferSize = 1024 * 1024

}
