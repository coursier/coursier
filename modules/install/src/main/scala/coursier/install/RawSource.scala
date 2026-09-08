package coursier.install

import dataclass.data

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import coursier.parse.RepositoryParser

/** Unprocessed source, meaning it's mostly made of strings rather than typed data.
  *
  * @param repositories
  * @param channel
  * @param id
  */
@data case class RawSource(
  repositories: List[String],
  channel: String,
  id: String
) {
  def source: ValidatedNel[String, Source] = {

    import RawAppDescriptor.validationNelToCats

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(repositories))

    val channelV = Validated.fromEither(
      Channel.parse(channel)
        .left.map(NonEmptyList.one)
    )

    (repositoriesV, channelV).mapN {
      (repositories, channel) =>
        Source(
          repositories,
          channel,
          id
        )
    }
  }
  def repr: String =
    Codecs.write(this)(RawSource.codec)
}

object RawSource {

  private final case class RawSourceJson(
    repositories: List[String],
    channel: String,
    id: String
  )

  private val jsonCodec: JsonValueCodec[RawSourceJson] =
    JsonCodecMaker.make(
      CodecMakerConfig
        .withRequireCollectionFields(true)
        .withTransientEmpty(false)
    )

  implicit val codec: JsonValueCodec[RawSource] =
    new JsonValueCodec[RawSource] {
      def decodeValue(in: JsonReader, default: RawSource): RawSource = {
        val json = jsonCodec.decodeValue(in, jsonCodec.nullValue)
        RawSource(json.repositories, json.channel, json.id)
      }
      def encodeValue(x: RawSource, out: JsonWriter): Unit =
        jsonCodec.encodeValue(RawSourceJson(x.repositories, x.channel, x.id), out)
      def nullValue: RawSource =
        null
    }

  def parse(input: String): Either[String, RawSource] =
    Codecs.read(input)(codec)

}
