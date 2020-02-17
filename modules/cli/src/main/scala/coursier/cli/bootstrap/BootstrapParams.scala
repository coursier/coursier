package coursier.cli.bootstrap

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.SharedLaunchParams
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

final case class BootstrapParams(
  sharedLaunch: SharedLaunchParams,
  nativeOptions: ScalaNativeOptions,
  nativeShortVersionOpt: Option[String] = None,
  specific: BootstrapSpecificParams
) {
  lazy val fork: Boolean =
    sharedLaunch.fork.getOrElse(SharedLaunchParams.defaultFork)
}

object BootstrapParams {
  def apply(options: BootstrapOptions): ValidatedNel[String, BootstrapParams] = {
    val sharedLaunchV = SharedLaunchParams(options.sharedLaunchOptions)
    val nativeOptionsV = options.nativeOptions.params
    val nativeVersionOpt = options.nativeOptions.nativeVersion.map(_.trim).filter(_.nonEmpty)
    val specificV = BootstrapSpecificParams(
      options.options,
      sharedLaunchV
        .toOption
        .exists(_.resolve.dependency.native)
    )

    (sharedLaunchV, nativeOptionsV, specificV).mapN {
      case (sharedLaunch, nativeOptions, specific) =>
        BootstrapParams(
          sharedLaunch,
          nativeOptions,
          nativeVersionOpt,
          specific
        )
    }
  }
}
