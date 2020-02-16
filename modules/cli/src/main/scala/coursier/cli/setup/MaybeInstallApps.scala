package coursier.cli.setup

import coursier.install.{Channels, InstallDir}
import coursier.util.Task
import dataclass.data

@data class MaybeInstallApps(
  installDir: InstallDir,
  channels: Channels,
  appIds: Seq[String]
) extends SetupStep {

  def banner: String =
    "Checking if the standard Scala applications are installed"

  def task: Task[Unit] = {

    val tasks = appIds
      .map { id =>
        for {
          appInfo <- channels.appDescriptor(id)
          installedOpt <- Task.delay(installDir.createOrUpdate(appInfo))
          _ <- {
            Task.delay {
              val message = installedOpt match {
                case None => s"Could not install $id (concurrent operation ongoing)"
                case Some(true) => s"Installed $id"
                case Some(false) => s"Found $id"
              }
              System.err.println("  " + message)
            }
          }
        } yield ()
      }

    tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t))
  }

  def tryRevert: Task[Unit] = {

    val tasks = appIds
      .map { id =>
        for {
          appInfo <- channels.appDescriptor(id)
          installedName = appInfo.appDescriptor.nameOpt.getOrElse(id)
          deletedOpt <- Task.delay(installDir.delete(installedName))
          _ <- {
            Task.delay {
              val message = deletedOpt match {
                case None => s"Could not delete $installedName (concurrent operation ongoing)"
                case Some(true) => s"Uninstalled $installedName"
                case Some(false) => s"$installedName was not installed"
              }
              System.err.println(message)
            }
          }
        } yield ()
      }

    tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t))
  }

}
