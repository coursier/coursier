package coursier.cli.bootstrap

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.install.SharedChannelParams
import coursier.cli.params.SharedLaunchParams
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

final case class BootstrapParams(
  sharedLaunch: SharedLaunchParams,
  nativeOptions: ScalaNativeOptions,
  channel: SharedChannelParams,
  nativeShortVersionOpt: Option[String] = None,
  specific: BootstrapSpecificParams
)

object BootstrapParams {
  def apply(options: BootstrapOptions): ValidatedNel[String, BootstrapParams] = {
    val sharedLaunchV    = SharedLaunchParams(options.sharedLaunchOptions)
    val nativeOptionsV   = options.nativeOptions.params
    val channelV         = SharedChannelParams(options.channelOptions)
    val nativeVersionOpt = options.nativeOptions.nativeVersion.map(_.trim).filter(_.nonEmpty)
    val specificV = BootstrapSpecificParams(
      options.options,
      sharedLaunchV
        .toOption
        .exists(_.resolve.dependency.native)
    )

    (sharedLaunchV, nativeOptionsV, channelV, specificV).mapN {
      case (sharedLaunch, nativeOptions, channel, specific) =>
        BootstrapParams(
          sharedLaunch,
          nativeOptions,
          channel,
          nativeVersionOpt,
          specific
        )
    }
  }
}
