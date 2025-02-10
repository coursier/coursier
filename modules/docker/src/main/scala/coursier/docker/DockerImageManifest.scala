package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class DockerImageManifest(
  config: DockerImageManifest.Config,
  layers: Seq[DockerImageLayer],
  schemaVersion: Int,
  mediaType: String
)

object DockerImageManifest {

  final case class Config(
    mediaType: String,
    size: Long,
    digest: String
  )

  implicit lazy val codec: JsonValueCodec[DockerImageManifest] =
    JsonCodecMaker.make

  def mediaType = DockerMediaType.json("image.manifest")
}
