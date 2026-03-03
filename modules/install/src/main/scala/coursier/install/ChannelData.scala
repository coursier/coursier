package coursier.install

import dataclass.data

import java.nio.charset.StandardCharsets

@data class ChannelData(
  channel: Channel,
  origin: String,
  data: Array[Byte]
) {
  lazy val strData: String =
    new String(data, StandardCharsets.UTF_8)
}
