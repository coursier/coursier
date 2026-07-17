package coursier.install

import java.nio.charset.StandardCharsets


final case class ChannelData(
  channel: Channel,
  origin: String,
  data: Array[Byte]
) {
  lazy val strData: String =
    new String(data, StandardCharsets.UTF_8)
}
