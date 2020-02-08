package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.install.Channel
import coursier.params.CacheParams

final case class InstallParams(
  cache: CacheParams,
  output: OutputParams,
  shared: SharedInstallParams,
  sharedChannel: SharedChannelParams,
  addChannels: Seq[Channel],
  installChannels: Seq[String],
  force: Boolean
) {
  lazy val channels: Seq[Channel] =
    (sharedChannel.channels ++ addChannels).distinct
}

object InstallParams {

  def apply(options: InstallOptions): ValidatedNel[String, InstallParams] = {

    val cacheParamsV = options.cacheOptions.params(None)
    val outputV = OutputParams(options.outputOptions)

    val sharedV = SharedInstallParams(options.sharedInstallOptions)

    val sharedChannelV = SharedChannelParams(options.sharedChannelOptions)

    val addChannelsV = options.addChannel.traverse { s =>
      val e = Channel.parse(s)
        .left.map(NonEmptyList.one)
        .map(c => (s, c))
      Validated.fromEither(e)
    }

    val force = options.force

    (cacheParamsV, outputV, sharedV, sharedChannelV, addChannelsV).mapN {
      (cacheParams, output, shared, sharedChannel, addChannels) =>
        InstallParams(
          cacheParams,
          output,
          shared,
          sharedChannel,
          addChannels.map(_._2),
          addChannels.map(_._1),
          force
        )
    }
  }
}
