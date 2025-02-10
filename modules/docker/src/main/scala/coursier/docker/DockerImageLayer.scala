package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class DockerImageLayer(
  size: Long,
  digest: String,
  mediaType: String
)

object DockerImageLayer {
  implicit lazy val codec: JsonValueCodec[DockerImageLayer] =
    JsonCodecMaker.make

  def mediaType = DockerMediaType.tarGz("image.layer")
}
