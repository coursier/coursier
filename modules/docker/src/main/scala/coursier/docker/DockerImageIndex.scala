package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

final case class DockerImageIndex(
  manifests: Seq[DockerImageIndex.Entry],
  mediaType: String,
  schemaVersion: Int
)

object DockerImageIndex {

  final case class Entry(
    annotations: Map[String, String],
    digest: String,
    mediaType: String,
    platform: Map[String, String],
    size: Long
  )

  implicit lazy val codec: JsonValueCodec[DockerImageIndex] =
    JsonCodecMaker.make

  def mediaType = DockerMediaType.json("image.index")
}
