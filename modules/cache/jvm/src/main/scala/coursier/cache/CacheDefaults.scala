package coursier.cache

import java.io.File
import java.nio.file.Path

import coursier.credentials.Credentials
import coursier.paths.CachePath
import coursier.util.Sync

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.Try

object CacheDefaults {

  lazy val location: File =
    CacheEnv.defaultCacheLocation(CacheEnv.cache.read()).toFile

  lazy val archiveCacheLocation: File =
    CacheEnv.defaultArchiveCacheLocation(CacheEnv.archiveCache.read()).toFile

  lazy val priviledgedArchiveCacheLocation: File =
    CachePath.defaultPriviledgedArchiveCacheDirectory()

  lazy val digestBasedCacheLocation: File = CachePath.defaultDigestBasedCacheDirectory()

  @deprecated(
    "Legacy cache location support was dropped, this method does nothing.",
    "2.0.0-RC6-22"
  )
  def warnLegacyCacheLocation(): Unit = {}

  private def defaultConcurrentDownloadCount = 6

  lazy val concurrentDownloadCount: Int =
    sys.props
      .get("coursier.parallel-download-count")
      .flatMap(s => Try(s.toInt).toOption)
      .getOrElse(
        defaultConcurrentDownloadCount
      )

  lazy val pool = Sync.fixedThreadPool(concurrentDownloadCount)

  def parseDuration(s: String): Either[Throwable, Duration] =
    CacheEnv.parseDuration(s)

  lazy val ttl: Option[Duration] =
    CacheEnv.defaultTtl(CacheEnv.ttl.read())

  // Check SHA-1 if available, else be fine with no checksum
  val checksums = Seq(Some("SHA-1"), None)

  def defaultRetryCount                       = 5
  private def defaultRetryBackoffInitialDelay = 10.milliseconds
  private def defaultRetryBackoffMultiplier   = 2.0

  lazy val retryCount =
    sys.props
      .get("coursier.exception-retry")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(_ >= 0)
      .getOrElse(defaultRetryCount)

  lazy val retryBackoffInitialDelay =
    sys.props
      .get("coursier.exception-retry-backoff-initial-delay")
      .flatMap(s => parseDuration(s).toOption)
      .collect {
        case f: FiniteDuration => f
      }
      .getOrElse(defaultRetryBackoffInitialDelay)

  lazy val retryBackoffMultiplier =
    sys.props
      .get("coursier.exception-retry-backoff-multiplier")
      .flatMap(s => scala.util.Try(s.toDouble).toOption)
      .filter(_ > 0)
      .getOrElse(defaultRetryBackoffMultiplier)

  @deprecated("Use retryCount instead", "2.1.11")
  lazy val sslRetryCount =
    sys.props
      .get("coursier.sslexception-retry")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(_ >= 0)
      .getOrElse(retryCount)

  private def defaultMaxRedirections = Option(20) // same default as java.net.HttpURLConnection
  lazy val maxRedirections: Option[Int] = {
    def prop(name: String) =
      sys.props
        .get(name)
        .flatMap(s => scala.util.Try(s.toInt).toOption)
        .filter(_ >= 0)
    prop("coursier.http.maxRedirects")
      .orElse(prop("http.maxRedirects")) // same property as java.net.HttpURLConnection
      .orElse(defaultMaxRedirections)
  }

  val bufferSize = 1024 * 1024

  lazy val credentials: Seq[Credentials] =
    CacheEnv.defaultCredentials(
      CacheEnv.credentials.read(),
      CacheEnv.scalaCliConfig.read(),
      CacheEnv.configDir.read()
    )

  def credentialsFromConfig(configPath: Path): Seq[Credentials] =
    CacheEnv.credentialsFromConfig(configPath)

  def noEnvCachePolicies: Seq[CachePolicy] =
    CacheEnv.noEnvCachePolicies

  lazy val cachePolicies: Seq[CachePolicy] =
    CacheEnv.defaultCachePolicies(CacheEnv.cachePolicy.read())
}
