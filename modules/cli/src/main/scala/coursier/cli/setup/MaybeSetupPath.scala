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

  import MaybeSetupPath.dirStr

  private def binDir = installDir.baseDir

  def banner: String =
    s"Checking if ${dirStr(installDir.baseDir)} is in PATH"

  def task: Task[Unit] = {

    val binDirStr = dirStr(binDir)

    // FIXME Messages may get out of sync with the actual update if it does more than adding to PATH
    val envUpdate = installDir.envUpdate

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
                profileUpdater.applyUpdate(envUpdate, MaybeSetupPath.headerComment)
              }
          }
      }
  }

  def tryRevert: Task[Unit] = {

    val envUpdate = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> binDir.toAbsolutePath.toString))

    val revertedTask = envVarUpdater match {
      case Left(windowsEnvVarUpdater) =>
        Task.delay {
          windowsEnvVarUpdater.tryRevertUpdate(envUpdate)
        }
      case Right(profileUpdater) =>
        val profileFilesStr = profileUpdater.profileFiles().map(dirStr)
        Task.delay {
          profileUpdater.tryRevertUpdate(MaybeSetupPath.headerComment)
        }
    }

    val profileFilesOpt = envVarUpdater match {
      case Left(windowsEnvVarUpdater) =>
        None
      case Right(profileUpdater) =>
        val profileFilesStr = profileUpdater.profileFiles().map(dirStr)
        Some(profileFilesStr)
    }

    revertedTask.flatMap { reverted =>
      val message =
        if (reverted) s"Removed $binDir from PATH" + profileFilesOpt.fold("")(l => s" in ${l.mkString(", ")}")
        else s"$binDir not setup in PATH"

      Task.delay(System.out.println(message))
    }
  }

}

object MaybeSetupPath {

  def dirStr(path: Path): String =
    path
      .toAbsolutePath
      .toString
      .replaceAllLiterally(sys.props("user.home"), "~")

  def headerComment = "coursier install directory"

}
