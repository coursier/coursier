package coursier.cache

import java.io.File

import coursier.CredentialFile
import coursier.parse.CachePolicyParser
import coursier.paths.CachePath
import coursier.util.Sync

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object CacheDefaults {

  lazy val location: File = CachePath.defaultCacheDirectory()

  private def defaultConcurrentDownloadCount = 6

  lazy val concurrentDownloadCount: Int =
    sys.props
      .get("coursier.parallel-download-count")
      .flatMap(s => Try(s.toInt).toOption)
      .getOrElse(
        defaultConcurrentDownloadCount
      )

  lazy val pool = Sync.fixedThreadPool(concurrentDownloadCount)

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

  def defaultRetryCount = 1

  val bufferSize = 1024 * 1024

  def credentialFiles: Seq[CredentialFile] =
    sys.env.get("COURSIER_CREDENTIALS")
      .orElse(sys.props.get("coursier.credentials"))
      .map { path =>
        CredentialFile(path, optional = true)
      }
      .toSeq

  val noEnvCachePolicies = Seq(
    // first, try to update changing artifacts that were previously downloaded (follows TTL)
    CachePolicy.LocalUpdateChanging,
    // then, use what's available locally
    CachePolicy.LocalOnly,
    // lastly, try to download what's missing
    CachePolicy.FetchMissing
  )

  def cachePolicies: Seq[CachePolicy] = {

    def fromOption(value: Option[String], description: String): Option[Seq[CachePolicy]] =
      value.filter(_.nonEmpty).flatMap {
        str =>
          CachePolicyParser.cachePolicies(str, noEnvCachePolicies).either match {
            case Right(Seq()) =>
              Console.err.println(
                s"Warning: no mode found in $description, ignoring it."
              )
              None
            case Right(policies) =>
              Some(policies)
            case Left(_) =>
              Console.err.println(
                s"Warning: unrecognized mode in $description, ignoring it."
              )
              None
          }
      }

    val fromEnv = fromOption(
      sys.env.get("COURSIER_MODE"),
      "COURSIER_MODE environment variable"
    )

    def fromProps = fromOption(
      sys.props.get("coursier.mode"),
      "Java property coursier.mode"
    )

    fromEnv
      .orElse(fromProps)
      .getOrElse(noEnvCachePolicies)
  }

}
