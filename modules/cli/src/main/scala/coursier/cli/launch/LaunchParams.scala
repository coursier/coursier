package coursier.cli.launch

import cats.data.ValidatedNel
import coursier.cli.params.SharedLaunchParams

final case class LaunchParams(
  shared: SharedLaunchParams,
  javaOptions: Seq[String],
  jep: Boolean,
  fetchCacheIKnowWhatImDoing: Option[String]
) {
  lazy val fork: Boolean =
    shared.fork.getOrElse(jep || javaOptions.nonEmpty || SharedLaunchParams.defaultFork)
}

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

    val sharedV = SharedLaunchParams(options.sharedOptions)

    sharedV.map { shared =>
      LaunchParams(
        shared,
        options.javaOpt,
        options.jep,
        options.fetchCacheIKnowWhatImDoing
      )
    }
  }
}
