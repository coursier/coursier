package coursier.cli.setup

import java.io.File

import coursier.cache.Cache
import coursier.env.{EnvironmentUpdate,FishUpdater,  ProfileUpdater, WindowsEnvVarUpdater}
import coursier.jvm.{JvmCacheLogger, JavaHome}
import coursier.util.Task
import dataclass.data

@data class MaybeInstallJvm(
  coursierCache: Cache[Task],
  envVarUpdaterOpt: Option[Either[WindowsEnvVarUpdater, Either[ProfileUpdater, FishUpdater]]],
  javaHome: JavaHome,
  confirm: Confirm,
  defaultId: String
) extends SetupStep {

  import MaybeInstallJvm.headerComment

  import MaybeSetupPath.dirStr

  def banner: String =
    "Checking if a JVM is installed"

  def task: Task[Unit] =
    for {
      initialIsSystemJavaHomeOpt <- javaHome.getWithIsSystemIfInstalled(defaultId)

      isSystemJavaHomeOpt <- initialIsSystemJavaHomeOpt match {
        case Some((isSystem, javaHome0)) =>
          System.err.println(s"Found a JVM installed under $javaHome0.") // Task.delay(…)
          Task.point(Some(isSystem -> javaHome0))
        case None =>
          confirm.confirm("No JVM found, should we try to install one?", default = true).flatMap {
            case false =>
              Task.point(None)
            case true =>
              javaHome.getWithIsSystem(defaultId).map(Some(_))
          }
      }

      envUpdate = isSystemJavaHomeOpt match {
        case Some((isSystem, javaHome0)) =>
          javaHome.environmentFor(isSystem, javaHome0)
        case None =>
          EnvironmentUpdate.empty
      }

      updatedSomething <- {

        envVarUpdaterOpt match {
          case None =>
            Task.delay {
              println(envUpdate.bashScript)
              false
            }
          case Some(Left(windowsEnvVarUpdater)) =>
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
          case Some(Right(Left(profileUpdater))) =>
            lazy val profileFiles = profileUpdater.profileFiles() // Task.delay(…)
            if (envUpdate.isEmpty || profileFiles.isEmpty /* just in case, should not happen */ )
              Task.point(false)
            else {
              val profileFilesStr =
                profileFiles.map(_.toString.replace(sys.props("user.home"), "~"))
              confirm.confirm(
                s"Should we update ${profileFilesStr.mkString(", ")}?",
                default = true
              ).flatMap {
                case false => Task.point(false)
                case true =>
                  Task.delay {
                    profileUpdater.applyUpdate(envUpdate, headerComment)
                  }
              }
            }
          case Some(Right(Right(fishUpdater))) =>
            lazy val profileFiles = fishUpdater.profileFiles() // Task.delay(…)
            if (envUpdate.isEmpty || profileFiles.isEmpty /* just in case, should not happen */)
              Task.point(false)
            else {
              val profileFilesStr = profileFiles.map(_.toString.replace(sys.props("user.home"), "~"))
              confirm.confirm(s"Should we update ${profileFilesStr.mkString(", ")}?", default = true).flatMap {
                case false => Task.point(false)
                case true =>
                  Task.delay {
                    fishUpdater.applyUpdate(envUpdate, headerComment)
                  }
              }
            }
        }
      }

      _ <- {
        if (updatedSomething)
          Task.delay {
            val messageStart =
              if (envVarUpdaterOpt.exists(_.isLeft))
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

  private def tryRevertEnvVarUpdate(
    envUpdate: EnvironmentUpdate,
    id: String
  ): Task[Unit] = {

    // FIXME Some duplication with MaybeSetupPath.revert

    val revertedTask = envVarUpdaterOpt match {
      case None =>
        Task.point(false)
      case Some(Left(windowsEnvVarUpdater)) =>
        Task.delay {
          windowsEnvVarUpdater.tryRevertUpdate(envUpdate)
        }
      case Some(Right(Left(profileUpdater))) =>
        val profileFilesStr = profileUpdater.profileFiles().map(dirStr)
        Task.delay {
          profileUpdater.tryRevertUpdate(headerComment)
        }
      case Some(Right(Right(fishUpdater))) =>
        val profileFilesStr = fishUpdater.profileFiles().map(dirStr)
        Task.delay {
          fishUpdater.tryRevertUpdate(headerComment)
        }
      
    }

    val profileFilesOpt = envVarUpdaterOpt.flatMap {
      case Left(windowsEnvVarUpdater) =>
        None
      case Right(Left(profileUpdater)) =>
        val profileFilesStr = profileUpdater.profileFiles().map(dirStr)
        Some(profileFilesStr)
      case Right(Right(fishUpdater)) =>
        val profileFilesStr = fishUpdater.profileFiles().map(dirStr)
        Some(profileFilesStr)
    }

    revertedTask.flatMap { reverted =>
      val message =
        if (reverted)
          s"Removed entries of JVM $id" + profileFilesOpt.fold("")(l => s" in ${l.mkString(", ")}")
        else
          s"JVM $id not setup"

      Task.delay(System.err.println(message))
    }
  }

  def tryRevert: Task[Unit] = {

    val maybeRemoveJvm = javaHome.cache
      .map { jvmCache =>
        val entryOpt = jvmCache.entries(defaultId)
          .unsafeRun()(coursierCache.ec) // meh
          .toOption
          .map(_.last)
        // replaces version ranges with actual versions in particular
        val id = entryOpt.fold(defaultId)(_.id)

        for {
          dirOpt <- jvmCache.getIfInstalled(id)
          removedOpt0 <- dirOpt match {
            case None => Task.point(Option(false))
            case Some(dir) =>
              ???
          }
          message = removedOpt0 match {
            case None        => s"Could not remove JVM $id (concurrent operation ongoing)"
            case Some(false) => s"JVM $id was not installed"
            case Some(true)  => s"Deleted JVM $id"
          }
          _ <- Task.delay(System.err.println(message))
          envUpdateOpt = dirOpt.map(dir => javaHome.environmentFor(false /* ??? */, dir))
          _ <- envUpdateOpt match {
            case None            => ???
            case Some(envUpdate) => tryRevertEnvVarUpdate(envUpdate, id)
          }
        } yield ()
      }
      .getOrElse(Task.point(()))

    maybeRemoveJvm
  }

}

object MaybeInstallJvm {

  def headerComment = "JVM installed by coursier"

}
