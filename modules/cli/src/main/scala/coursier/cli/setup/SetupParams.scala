package coursier.cli.setup

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.install.{SharedChannelParams, SharedInstallParams}
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.OutputParams
import coursier.params.CacheParams

final case class SetupParams(
  sharedJava: SharedJavaParams,
  sharedInstall: SharedInstallParams,
  sharedChannel: SharedChannelParams,
  cache: CacheParams,
  output: OutputParams,
  homeOpt: Option[Path]
)

object SetupParams {
  def apply(options: SetupOptions): ValidatedNel[String, SetupParams] = {
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)
    val sharedInstallV = SharedInstallParams(options.sharedInstallOptions)
    val sharedChannelV = SharedChannelParams(options.sharedChannelOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val homeOpt = options.home.filter(_.nonEmpty).map(Paths.get(_))
    (sharedJavaV, sharedInstallV, sharedChannelV, cacheV, outputV).mapN { (sharedJava, sharedInstall, sharedChannel, cache, output) =>
      SetupParams(
        sharedJava,
        sharedInstall,
        sharedChannel,
        cache,
        output,
        homeOpt
      )
    }
  }
}
