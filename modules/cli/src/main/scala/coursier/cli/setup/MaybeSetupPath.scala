package coursier.cli.setup

import java.nio.file.Path

import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.install.InstallDir
import coursier.util.Task
import dataclass.data

@data class MaybeSetupPath(
  installDir: InstallDir,
  envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
  getEnv: String => Option[String],
  pathSeparator: String,
  confirm: Confirm
) extends SetupStep {

  private def binDir = installDir.baseDir

  private def dirStr(path: Path): String =
    path
      .toAbsolutePath
      .toString
      .replaceAllLiterally(sys.props("user.home"), "~")

  def banner: String =
    s"Checking if ${dirStr(installDir.baseDir)} is in PATH"

  def task: Task[Unit] = {

    val binDirStr = dirStr(binDir)

    val envUpdate = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> binDir.toAbsolutePath.toString))

    val alreadyApplied = envUpdate.alreadyApplied(getEnv, pathSeparator)

    if (alreadyApplied)
      Task.point(())
    else
      envVarUpdater match {
        case Left(windowsEnvVarUpdater) =>
          confirm.confirm(s"Should we add $binDirStr to your PATH?", default = true).flatMap {
            case false => Task.point(())
            case true =>
              Task.delay {
                windowsEnvVarUpdater.applyUpdate(envUpdate)
              }
          }
        case Right(profileUpdater) =>
          val profileFilesStr = profileUpdater.profileFiles().map(dirStr)
          confirm.confirm(s"Should we add $binDirStr to your PATH via ${profileFilesStr.mkString(", ")}?", default = true).flatMap {
            case false => Task.point(())
            case true =>
              Task.delay {
                profileUpdater.applyUpdate(envUpdate, "coursier install directory")
              }
          }
      }
  }

}
