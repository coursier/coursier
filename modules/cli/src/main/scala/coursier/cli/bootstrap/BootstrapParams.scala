package coursier.cli.bootstrap

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.install.SharedChannelParams
import coursier.cli.params.SharedLaunchParams
import coursier.launcher.ScalaNativeLauncherUnsupported

final case class BootstrapParams(
  jvmOptionFile: Option[String],
  sharedLaunch: SharedLaunchParams,
  channel: SharedChannelParams,
  specific: BootstrapSpecificParams
)

object BootstrapParams {
  def apply(options: BootstrapOptions): ValidatedNel[String, BootstrapParams] = {
    val sharedLaunchV = SharedLaunchParams(options.sharedLaunchOptions)
    val channelV      = SharedChannelParams(options.channelOptions)
    val specificV = BootstrapSpecificParams(
      options.options,
      sharedLaunchV
        .toOption
        .exists(_.resolve.dependency.native)
    )
    val noScalaNativeV =
      if (sharedLaunchV.toOption.exists(_.resolve.dependency.native))
        Validated.invalidNel(ScalaNativeLauncherUnsupported.message)
      else
        Validated.validNel(())

    (sharedLaunchV, channelV, specificV, noScalaNativeV).mapN {
      case (sharedLaunch, channel, specific, ()) =>
        val jvmOptionFile = options.jvmOptionFile.map(_.trim).filter(_.nonEmpty)
        BootstrapParams(
          jvmOptionFile,
          sharedLaunch,
          channel,
          specific
        )
    }
  }
}
