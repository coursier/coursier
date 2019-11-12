package coursier.cli.launch

import cats.data.ValidatedNel
import coursier.cli.params.SharedLaunchParams

final case class LaunchParams(
  shared: SharedLaunchParams,
  jep: Boolean
) {
  lazy val fork: Boolean =
    shared.fork.getOrElse(jep || SharedLaunchParams.defaultFork)
}

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

    val sharedV = SharedLaunchParams(options.sharedOptions)

    sharedV.map { shared =>
      LaunchParams(
        shared,
        options.jep
      )
    }
  }
}
