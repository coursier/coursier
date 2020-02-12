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
          installed <- Task.delay(installDir.createOrUpdate(appInfo))
          _ <- {
            Task.delay {
              val message =
                if (installed) s"Installed $id"
                else s"Found $id"
              System.out.println("  " + message)
            }
          }
        } yield ()
      }

    tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t))
  }

}
