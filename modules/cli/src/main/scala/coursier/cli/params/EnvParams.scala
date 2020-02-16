package coursier.cli.params

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.EnvOptions
import coursier.env.{ProfileUpdater, WindowsEnvVarUpdater}
import coursier.launcher.internal.Windows

final case class EnvParams(
  env: Boolean,
  setup: Boolean,
  homeOpt: Option[Path]
) {
  def anyFlag: Boolean = env || setup
  // TODO Allow to customize some parameters of WindowsEnvVarUpdater / ProfileUpdater?
  def envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater] =
    if (Windows.isWindows)
      Left(WindowsEnvVarUpdater())
    else
      Right(
        ProfileUpdater()
          .withHome(homeOpt.orElse(ProfileUpdater.defaultHome))
      )
}

object EnvParams {
  def apply(options: EnvOptions): ValidatedNel[String, EnvParams] = {
    val homeOpt = options.userHome.filter(_.nonEmpty).map(Paths.get(_))

    val flags = Seq(
      options.env,
      options.setup
    )
    val flagsV =
      if (flags.count(identity) > 1)
        Validated.invalidNel("Error: can only specify one of --env, --setup.")
      else
        Validated.validNel(())

    flagsV.map { _ =>
      EnvParams(
        options.env,
        options.setup,
        homeOpt
      )
    }
  }
}
