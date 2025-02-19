package coursier.clitests.util

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.internal.FileUtil

import java.net.URL

object DockerTestUtil {

  def token(repoName: String): String = {
    // FIXME repo escaping
    val url =
      s"https://auth.docker.io/token?service=registry.docker.io&scope=repository:$repoName:pull"
    val conn = new URL(url).openConnection()
    val b    = FileUtil.readFully(conn.getInputStream())
    readFromArray(b)(tokenResponseCodec).token
  }

  private final case class TokenResponse(
    token: String
  )

  private implicit lazy val tokenResponseCodec: JsonValueCodec[TokenResponse] =
    JsonCodecMaker.make

}
