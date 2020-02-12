package coursier.cli.install

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.install.InstallDir
import coursier.cache.Cache
import coursier.util.Task
import coursier.util.EitherT
import coursier.util.Artifact
import coursier.cache.ArtifactError
import java.io.File
import scala.concurrent.ExecutionContext
import coursier.install.Updatable

object Uninstall extends CaseApp[UninstallOptions] {

  private final class NoopCache extends Cache[Task] {
    def ec = ExecutionContext.global
    def fetch: Cache.Fetch[Task] =
      _ => EitherT(Task.point[Either[String, String]](Left("unexpected download attempt")))
    def file(artifact: Artifact): EitherT[Task, ArtifactError, File] =
      EitherT(Task.point[Either[ArtifactError, File]](Left(new ArtifactError.DownloadError("unexpected download attempt", None))))
  }


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
      if (params.all) Updatable.list(params.dir)
      else args0

    if (list.isEmpty) {
      if (params.verbosity >= 0)
        System.err.println("Nothing to remove")
    } else
      for (app <- list) {
        val resOpt = installDir.delete(app)
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
