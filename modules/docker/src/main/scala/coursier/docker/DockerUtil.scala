package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.{Cache, CacheLogger}
import coursier.cache.internal.FileUtil
import coursier.util.{Artifact, Task}

import java.io.File
import java.net.URL

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DockerUtil {

  def token(
    authRegistry: String,
    repoName: String
  ): String = {
    // FIXME repo escaping!!!!
    val url  = s"$authRegistry?service=registry.docker.io&scope=repository:$repoName:pull"
    val conn = new URL(url).openConnection()
    val b    = FileUtil.readFully(conn.getInputStream())
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
