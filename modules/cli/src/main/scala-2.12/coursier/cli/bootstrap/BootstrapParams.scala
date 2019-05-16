package coursier.cli.bootstrap

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.SharedLaunchParams

final case class BootstrapParams(
  sharedLaunch: SharedLaunchParams,
  nativeBootstrap: NativeBootstrapParams,
  specific: BootstrapSpecificParams
)

object BootstrapParams {
  def apply(options: BootstrapOptions): ValidatedNel[String, BootstrapParams] = {
    val sharedLaunchV = SharedLaunchParams(options.sharedLaunchOptions)
    val nativeBootstrapV = NativeBootstrapParams(options.nativeOptions)
    val specificV = BootstrapSpecificParams(options.options)

    (sharedLaunchV, nativeBootstrapV, specificV).mapN {
      case (sharedLaunch, nativeBootstrap, specific) =>
        BootstrapParams(
          sharedLaunch,
          nativeBootstrap,
          specific
        )
    }
  }
}
