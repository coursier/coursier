package coursier.install

import argonaut.{DecodeJson, EncodeJson, Parse}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.parse.RepositoryParser
import dataclass.data

/**
  * Unprocessed source, meaning it's mostly made of strings rather than typed data.
  *
  * @param repositories
  * @param channel
  * @param id
  */
@data class RawSource(
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

  import argonaut.ArgonautShapeless._

  implicit val encoder = EncodeJson.of[RawSource]
  implicit val decoder = DecodeJson.of[RawSource]

  def parse(input: String): Either[String, RawSource] =
    Parse.decodeEither(input)(decoder)

}
