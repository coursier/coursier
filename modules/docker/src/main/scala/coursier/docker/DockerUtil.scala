package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.CacheDefaults
import coursier.cache.internal.{FileUtil, Retry}

import java.io.IOException
import java.net.URL

import scala.concurrent.duration.DurationInt

object DockerUtil {

  private def tokenRetry =
    Retry(
      CacheDefaults.retryCount,
      1.second,
      CacheDefaults.retryBackoffMultiplier
    )

  def token(
    authRegistry: String,
    repoName: String
  ): String = {
    // FIXME repo escaping!!!!
    val url = s"$authRegistry?service=registry.docker.io&scope=repository:$repoName:pull"
    val b   = tokenRetry.retry {
      val conn = new URL(url).openConnection()
      FileUtil.readFully(conn.getInputStream())
    } {
      case _: IOException =>
    }
    readFromArray(b)(tokenResponseCodec).token
  }

  def repoNameVersion(imageName: String): (String, String) = {

    val (repoName0, repoVersion) =
      if (imageName.count(_ == ':') <= 1)
        imageName.split(':') match {
          case Array(name)      => (name, "latest")
          case Array(name, ver) => (name, ver)
        }
      else
        sys.error(s"Malformed docker repo '$imageName' (expected name:version)")

    val repoName =
      if (repoName0.contains("/")) repoName0
      else "library/" + repoName0

    (repoName, repoVersion)
  }

  def repoNameVersion(args: Seq[String]): (String, String) =
    args match {
      case Seq() =>
        sys.error("No repository specified on the command-line")
      case Seq(repo0) =>
        repoNameVersion(repo0)
      case other =>
        sys.error("Too many arguments passed as repository, expected one")
    }

  private final case class TokenResponse(
    token: String
  )

  private implicit lazy val tokenResponseCodec: JsonValueCodec[TokenResponse] =
    JsonCodecMaker.make

}
