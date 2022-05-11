package coursier.cli.params

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.EnvOptions
import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.launcher.internal.Windows
import coursier.util.Task

import scala.util.Properties

final case class EnvParams(
  env: Boolean,
  disableEnv: Boolean,
  setup: Boolean,
  homeOpt: Option[Path],
  windowsScript: Boolean
) {
  def anyFlag: Boolean = env || setup
  // TODO Allow to customize some parameters of WindowsEnvVarUpdater / ProfileUpdater?
  def envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater] =
    if (Properties.isWin)
      Left(WindowsEnvVarUpdater().withUseJni(Some(coursier.paths.Util.useJni())))
    else
      Right(
        ProfileUpdater()
          .withHome(homeOpt.orElse(ProfileUpdater.defaultHome))
      )

  def setupTask(
    envUpdate: EnvironmentUpdate,
    envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
    verbosity: Int,
    headerComment: String
  ): Task[Unit] =
    for {

      updatedSomething <- {

        if (envUpdate.isEmpty) Task.point(false)
        else
          envVarUpdater match {
            case Left(windowsEnvVarUpdater) =>
              val msg = s"Checking if the " +
                (envUpdate.set.map(_._1) ++ envUpdate.pathLikeAppends.map(_._1)).mkString(", ") +
                " user environment variable(s) need(s) updating."
              Task.delay {
                if (verbosity >= 0)
                  System.err.println(msg)
                windowsEnvVarUpdater.applyUpdate(envUpdate)
              }
            case Right(profileUpdater) =>
              lazy val profileFiles = profileUpdater.profileFiles() // Task.delay(…)
              val profileFilesStr =
                profileFiles.map(_.toString.replace(sys.props("user.home"), "~"))
              val msg = s"Checking if ${profileFilesStr.mkString(", ")} need(s) updating."
              Task.delay {
                if (verbosity >= 0)
                  System.err.println(msg)
                profileUpdater.applyUpdate(envUpdate, headerComment)
              }
          }
      }

      _ <- {
        if (updatedSomething && verbosity >= 0)
          Task.delay {
            val messageStart =
              if (envVarUpdater.isLeft)
                "Some global environment variables were updated."
              else
                "Some shell configuration files were updated."

            val message =
              messageStart + " It is recommended to close this terminal once " +
                "the setup command is done, and open a new one " +
                "for the changes to be taken into account."

            System.err.println(message)
          }
        else
          Task.point(())
      }

    } yield ()
}

object EnvParams {
  def apply(options: EnvOptions): ValidatedNel[String, EnvParams] = {
    val homeOpt = options.userHome.filter(_.nonEmpty).map(Paths.get(_))

    val flags = Seq(
      options.env,
      options.disableEnv,
      options.setup
    )
    val flagsV =
      if (flags.count(identity) > 1)
        Validated.invalidNel(
          "Error: can only specify one of --env, --disable / --disable-env, --setup."
        )
      else
        Validated.validNel(())

    flagsV.map { _ =>
      EnvParams(
        options.env,
        options.disableEnv,
        options.setup,
        homeOpt,
        options.windowsScript
      )
    }
  }
}
