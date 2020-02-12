package coursier.cli.setup

import coursier.cache.Cache
import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.jvm.{JvmCacheLogger, JavaHome}
import coursier.util.Task
import dataclass.data

@data class MaybeInstallJvm(
  coursierCache: Cache[Task],
  envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
  javaHome: JavaHome,
  confirm: Confirm
) extends SetupStep {

  def banner: String =
    "Checking if a JVM is installed"

  def task: Task[Unit] =
    for {
      javaHomeOpt <- javaHome.system()

      idJavaHomeOpt <- javaHomeOpt match {
        case Some(javaHome0) =>
          System.out.println(s"Found a JVM installed under $javaHome0.") // Task.delay(…)
          Task.point(Some(JavaHome.systemId -> javaHome0))
        case None =>
          confirm.confirm("No system JVM found, should we try to install one?", default = true).flatMap {
            case false =>
              Task.point(None)
            case true =>
              javaHome.getWithRetainedId(JavaHome.defaultJvm).map(Some(_))
          }
      }

      envUpdate = idJavaHomeOpt match {
        case Some((id, javaHome0)) =>
          javaHome.environmentFor(id, javaHome0)
        case None =>
          EnvironmentUpdate.empty
      }

      updatedSomething <- {

        envVarUpdater match {
          case Left(windowsEnvVarUpdater) =>
            if (envUpdate.isEmpty) Task.point(false)
            else {
              val msg = s"Should we update the " +
                (envUpdate.set.map(_._1) ++ envUpdate.pathLikeAppends.map(_._1)).mkString(", ") +
                " environment variable(s)?"
              confirm.confirm(msg, default = true)
                .flatMap {
                  case false => Task.point(false)
                  case true =>
                    Task.delay {
                      windowsEnvVarUpdater.applyUpdate(envUpdate)
                    }
                }
            }
          case Right(profileUpdater) =>
            lazy val profileFiles = profileUpdater.profileFiles() // Task.delay(…)
            if (envUpdate.isEmpty || profileFiles.isEmpty /* just in case, should not happen */)
              Task.point(false)
            else {
              val profileFilesStr = profileFiles.map(_.toString.replaceAllLiterally(sys.props("user.home"), "~"))
              confirm.confirm(s"Should we update ${profileFilesStr.mkString(", ")}?", default = true).flatMap {
                case false => Task.point(false)
                case true =>
                  Task.delay {
                    profileUpdater.applyUpdate(envUpdate, "JVM installed by coursier")
                  }
              }
            }
        }
      }

      _ <- {
        if (updatedSomething)
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

            println(message)
          }
        else
          Task.point(())
      }

    } yield ()
}
