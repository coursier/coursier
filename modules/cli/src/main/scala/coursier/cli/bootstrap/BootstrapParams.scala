package coursier.cli.bootstrap

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.app.Platform
import coursier.cli.native.NativeLauncherParams
import coursier.cli.params.SharedLaunchParams

final case class BootstrapParams(
  sharedLaunch: SharedLaunchParams,
  nativeBootstrap: NativeLauncherParams,
  specific: BootstrapSpecificParams
) {
  lazy val fork: Boolean =
    sharedLaunch.fork.getOrElse(SharedLaunchParams.defaultFork)
}

object BootstrapParams {
  def apply(options: BootstrapOptions): ValidatedNel[String, BootstrapParams] = {
    val sharedLaunchV = SharedLaunchParams(options.sharedLaunchOptions)
    val nativeBootstrapV = options.nativeOptions.params
    val specificV = BootstrapSpecificParams(
      options.options,
      sharedLaunchV
        .toOption
        .exists(_.resolve.dependency.native)
    )

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
