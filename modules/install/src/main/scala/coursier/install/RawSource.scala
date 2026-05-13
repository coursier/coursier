package coursier.install

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.github.plokhotnyuk.jsoniter_scala.core.{
  JsonReaderError,
  JsonValueCodec,
  readFromString,
  writeToString
}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.parse.RepositoryParser
import dataclass.data

/** Unprocessed source, meaning it's mostly made of strings rather than typed data.
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
    writeToString(this)(RawSource.codec)
}

object RawSource {

  lazy val codec: JsonValueCodec[RawSource] = JsonCodecMaker.make

  def parse(input: String): Either[String, RawSource] =
    try Right(readFromString(input)(codec))
    catch {
      case err: JsonReaderError =>
        Left(err.toString)
    }

}
