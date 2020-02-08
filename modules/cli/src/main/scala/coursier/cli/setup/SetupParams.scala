package coursier.cli.setup

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.install.{SharedChannelParams, SharedInstallParams}
import coursier.cli.jvm.SharedJavaParams

final case class SetupParams(
  sharedJava: SharedJavaParams,
  sharedInstall: SharedInstallParams,
  sharedChannel: SharedChannelParams,
  homeOpt: Option[Path]
)

object SetupParams {
  def apply(options: SetupOptions): ValidatedNel[String, SetupParams] = {
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)
    val sharedInstallV = SharedInstallParams(options.sharedInstallOptions)
    val sharedChannelV = SharedChannelParams(options.sharedChannelOptions)
    val homeOpt = options.home.filter(_.nonEmpty).map(Paths.get(_))
    (sharedJavaV, sharedInstallV, sharedChannelV).mapN { (sharedJava, sharedInstall, sharedChannel) =>
      SetupParams(
        sharedJava,
        sharedInstall,
        sharedChannel,
        homeOpt
      )
    }
  }
}
