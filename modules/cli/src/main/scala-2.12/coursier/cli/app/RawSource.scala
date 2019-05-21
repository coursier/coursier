package coursier.cli.app

import argonaut.{DecodeJson, EncodeJson, Parse}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.parse.{JavaOrScalaModule, ModuleParser, RepositoryParser}

final case class RawSource(
  repositories: List[String],
  channel: String,
  id: String
) {
  def source: ValidatedNel[String, Source] = {

    import RawAppDescriptor.validationNelToCats

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(repositories))

    val channelV = Validated.fromEither(
      ModuleParser.javaOrScalaModule(channel)
        .right.flatMap {
          case _: JavaOrScalaModule.ScalaModule => Left("Scala modules not accepted for channels")
          case m: JavaOrScalaModule.JavaModule => Right(m.module)
        }
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
