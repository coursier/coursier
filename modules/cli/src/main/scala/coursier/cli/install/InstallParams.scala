package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.{CacheParams, EnvParams, OutputParams}
import coursier.install.Channel

final case class InstallParams(
  cache: CacheParams,
  output: OutputParams,
  shared: SharedInstallParams,
  sharedChannel: SharedChannelParams,
  sharedJava: SharedJavaParams,
  env: EnvParams,
  addChannels: Seq[Channel],
  installChannels: Seq[String],
  force: Boolean
) {
  lazy val channels: Seq[Channel] =
    (sharedChannel.channels ++ addChannels).distinct
}

object InstallParams {

  def apply(options: InstallOptions, anyArg: Boolean): ValidatedNel[String, InstallParams] = {

    val cacheParamsV = options.cacheOptions.params(None)
    val outputV = OutputParams(options.outputOptions)

    val sharedV = SharedInstallParams(options.sharedInstallOptions)

    val sharedChannelV = SharedChannelParams(options.sharedChannelOptions)
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)

    val envV = EnvParams(options.envOptions)

    val addChannelsV = options.addChannel.traverse { s =>
      val e = Channel.parse(s)
        .left.map(NonEmptyList.one)
        .map(c => (s, c))
      Validated.fromEither(e)
    }

    val force = options.force

    val checkNeedsChannelsV =
      if (anyArg && sharedChannelV.toOption.exists(_.channels.isEmpty) && addChannelsV.toOption.exists(_.isEmpty))
        Validated.invalidNel(s"Error: no channels specified")
      else
        Validated.validNel(())

    val flags = Seq(
      options.addChannel.nonEmpty,
      envV.toOption.fold(false)(_.anyFlag)
    )
    val flagsV =
      if (flags.count(identity) > 1)
        Validated.invalidNel("Error: can only specify one of --add-channel, --env, --setup.")
      else
        Validated.validNel(())

    val checkArgsV =
      if (anyArg && flags.exists(identity))
        Validated.invalidNel(s"Error: unexpected arguments passed along --add-channel, --env, or --setup.")
      else
        Validated.validNel(())

    (cacheParamsV, outputV, sharedV, sharedChannelV, sharedJavaV, envV, addChannelsV, checkNeedsChannelsV, flagsV, checkArgsV).mapN {
      (cacheParams, output, shared, sharedChannel, sharedJava, env, addChannels, _, _, _) =>
        InstallParams(
          cacheParams,
          output,
          shared,
          sharedChannel,
          sharedJava,
          env,
          addChannels.map(_._2),
          addChannels.map(_._1),
          force
        )
    }
  }
}
