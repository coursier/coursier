package coursier.cache

import java.io.File

import coursier.CacheParse
import coursier.core.Repository
import coursier.paths.CachePath
import coursier.util.{Repositories, Schedulable}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object CacheDefaults {

  lazy val location: File = CachePath.defaultCacheDirectory()

  private def defaultConcurrentDownloadCount = 6

  lazy val concurrentDownloadCount: Int =
    sys
      .props
      .get("coursier.parallel-download-count")
      .flatMap(s => Try(s.toInt).toOption)
      .getOrElse(
        defaultConcurrentDownloadCount
      )

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
    sys
      .props
      .get("coursier.sslexception-retry")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(_ >= 0)
      .getOrElse(defaultSslRetryCount)

  def defaultRetryCount = 1

  val bufferSize = 1024 * 1024

  lazy val defaultRepositories: Seq[Repository] = {

    def fromString(str: String, origin: String): Option[Seq[Repository]] = {

      val l = str
        .split('|')
        .toSeq
        .filter(_.nonEmpty)

      CacheParse.repositories(l).either match {
        case Left(errs) =>
          System
            .err.println(
              s"Ignoring $origin, error parsing repositories from it:\n" +
                errs.map("  " + _ + "\n").mkString
            )
          None
        case Right(repos) =>
          Some(repos)
      }
    }

    val fromEnvOpt = sys
      .env
      .get("COURSIER_REPOSITORIES")
      .filter(_.nonEmpty)
      .flatMap(fromString(_, "environment variable COURSIER_REPOSITORIES"))

    val fromPropsOpt = sys
      .props
      .get("coursier.repositories")
      .filter(_.nonEmpty)
      .flatMap(fromString(_, "Java property coursier.repositories"))

    val default = Seq(
      LocalRepositories.ivy2Local,
      Repositories.central
    )

    fromEnvOpt
      .orElse(fromPropsOpt)
      .getOrElse(default)
  }

}
