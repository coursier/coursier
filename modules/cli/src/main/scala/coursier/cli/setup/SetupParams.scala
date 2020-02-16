package coursier.cli.setup

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.install.{SharedChannelParams, SharedInstallParams}
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.{CacheParams, EnvParams, OutputParams}

final case class SetupParams(
  sharedJava: SharedJavaParams,
  sharedInstall: SharedInstallParams,
  sharedChannel: SharedChannelParams,
  cache: CacheParams,
  output: OutputParams,
  env: EnvParams,
  banner: Boolean,
  yes: Boolean,
  tryRevert: Boolean,
  apps: Seq[String]
)

object SetupParams {
  def apply(options: SetupOptions): ValidatedNel[String, SetupParams] = {
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)
    val sharedInstallV = SharedInstallParams(options.sharedInstallOptions)
    val sharedChannelV = SharedChannelParams(options.sharedChannelOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val envV = EnvParams(options.envOptions)
    val banner = options.banner.getOrElse(false)
    val yes = options.yes
    val tryRevert = options.tryRevert
    val apps = Some(options.apps.flatMap(_.split(',').toSeq).map(_.trim).filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .getOrElse(DefaultAppList.defaultAppList)
    (sharedJavaV, sharedInstallV, sharedChannelV, cacheV, outputV, envV).mapN { (sharedJava, sharedInstall, sharedChannel, cache, output, env) =>
      SetupParams(
        sharedJava,
        sharedInstall,
        sharedChannel,
        cache,
        output,
        env,
        banner,
        yes,
        tryRevert,
        apps
      )
    }
  }
}
