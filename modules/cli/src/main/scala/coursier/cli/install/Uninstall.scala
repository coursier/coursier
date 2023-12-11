package coursier.cli.install

import java.io.File
import java.io.FileNotFoundException

import caseapp.core.RemainingArgs
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.InstallDir
import coursier.cache.Cache
import coursier.install.error.InstallDirException
import coursier.util.Task
import coursier.util.EitherT
import coursier.util.Artifact
import coursier.cache.ArtifactError
import scala.concurrent.ExecutionContext
import java.nio.file.NoSuchFileException

object Uninstall extends CoursierCommand[UninstallOptions] {

  override def group: String = CommandGroup.install

  def run(options: UninstallOptions, args: RemainingArgs): Unit = {

    val params = UninstallParams(options).exitOnError()

    val args0 = args.all

    if (args0.isEmpty && !params.all) {
      System.err.println("Error: no application to uninstall or --all specified.")
      sys.exit(1)
    }

    if (args0.nonEmpty && params.all) {
      System.err.println(s"Error: cannot pass applications to uninstall along with --all")
      sys.exit(1)
    }

    if (params.verbosity >= 1)
      System.err.println(s"Using install directory ${params.dir}")

    val installDir = InstallDir(params.dir, new NoopCache)
      .withVerbosity(params.verbosity)

    val list =
      if (params.all) installDir.list()
      else args0

    if (list.isEmpty) {
      if (params.verbosity >= 0)
        System.err.println("Nothing to remove")
    }
    else
      for (app <- list) {
        val resOpt =
          try installDir.delete(app)
          catch {
            case e: InstallDirException if params.verbosity <= 1 =>
              System.err.println(e.getMessage)
              sys.exit(1)
            case _: FileNotFoundException | _: NoSuchFileException =>
              System.err.println(s"$app is not installed by coursier")
              sys.exit(1)
          }
        resOpt match {
          case None =>
            if (params.verbosity >= 0)
              System.err.println(s"Could not uninstall $app (concurrent operation ongoing)")
          case Some(true) =>
            if (params.verbosity >= 0)
              System.err.println(s"Uninstalled $app")
          case Some(false) =>
            if (params.verbosity >= 1)
              System.err.println(s"Nothing to uninstall for $app")
        }
      }
  }
}
