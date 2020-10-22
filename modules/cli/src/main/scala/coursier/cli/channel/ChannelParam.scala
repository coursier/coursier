package coursier.cli.channel

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.OutputParams

final case class ChannelParam(
  addChannel: List[String],
  listChannels: Boolean,
  output: OutputParams
)

object ChannelParam {
  def apply(options: ChannelOptions, anyArg: Boolean): ValidatedNel[String, ChannelParam] = {

    val addChannelsV = options.add.traverse { s =>
      val e = coursier.install.Channel
        .parse(s)
        .left
        .map(NonEmptyList.one)
        .map(_ => s)

      Validated.fromEither(e)
    }

    val outputParamsV = OutputParams(options.outputOptions)

    (addChannelsV, outputParamsV).mapN { (addChannels, outputParams) =>
      ChannelParam(addChannels, options.list, outputParams)
    }
  }
}
