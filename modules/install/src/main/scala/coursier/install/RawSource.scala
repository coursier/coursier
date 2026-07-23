package coursier.install

import dataclass.data

import argonaut.{DecodeJson, EncodeJson, Parse}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
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
    RawSource.encoder.encode(this).nospaces
}

object RawSource {

  import argonaut.Argonaut._

  lazy val codec: argonaut.CodecJson[RawSource] =
    argonaut.CodecJson.casecodec3(RawSource.apply, (s: RawSource) => Some((s.repositories, s.channel, s.id)))(
      "repositories",
      "channel",
      "id"
    )
  lazy val encoder: EncodeJson[RawSource] = codec.Encoder
  lazy val decoder: DecodeJson[RawSource] = codec.Decoder

  def parse(input: String): Either[String, RawSource] =
    Parse.decodeEither(input)(using decoder)

}
