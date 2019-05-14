package coursier.cli.params

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.options.BootstrapOptions
import coursier.cli.params.shared.SharedLaunchParams

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
