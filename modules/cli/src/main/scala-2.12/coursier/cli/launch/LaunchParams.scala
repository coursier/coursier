package coursier.cli.launch

import cats.data.ValidatedNel
import coursier.cli.params.shared.SharedLaunchParams

final case class LaunchParams(
  shared: SharedLaunchParams
)

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

    val sharedV = SharedLaunchParams(options.sharedOptions)

    sharedV.map { shared =>
      LaunchParams(shared)
    }
  }
}
