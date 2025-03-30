package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class DockerImageConfig(
  architecture: String,
  config: DockerImageConfig.Config,
  os: String,
  variant: Option[String] = None
)

object DockerImageConfig {

  final case class Config(
    Env: Seq[String],
    Cmd: Seq[String],
    WorkingDir: Option[String] = None
  )

  implicit lazy val codec: JsonValueCodec[DockerImageConfig] =
    JsonCodecMaker.make

  def mediaType = DockerMediaType.json("image.config")
}
