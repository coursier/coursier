package coursier.cli.jvm

import java.io.File

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.setup.MaybeInstallJvm
import coursier.cli.Util.ValidatedExitOnError
import coursier.env.{EnvironmentUpdate, EnvVarUpdater, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.jvm.{JvmCache, JvmCacheLogger}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

object JavaHome extends CaseApp[JavaHomeOptions] {

  def setup(
    envUpdate: EnvironmentUpdate,
    envVarUpdater: Either[WindowsEnvVarUpdater, ProfileUpdater],
    verbosity: Int
  ): Task[Unit] =
    for {

      updatedSomething <- {

        if (envUpdate.isEmpty) Task.point(false)
        else
          envVarUpdater match {
            case Left(windowsEnvVarUpdater) =>
              val msg = s"Updating the " +
                (envUpdate.set.map(_._1) ++ envUpdate.pathLikeAppends.map(_._1)).mkString(", ") +
                " user environment variable(s)."
              Task.delay {
                if (verbosity >= 0)
                  println(msg)
                windowsEnvVarUpdater.applyUpdate(envUpdate)
              }
            case Right(profileUpdater) =>
              lazy val profileFiles = profileUpdater.profileFiles() // Task.delay(…)
              val profileFilesStr = profileFiles.map(_.toString.replaceAllLiterally(sys.props("user.home"), "~"))
              val msg = s"Updating ${profileFilesStr.mkString(", ")}"
              Task.delay {
                if (verbosity >= 0)
                  println(msg)
                profileUpdater.applyUpdate(envUpdate, MaybeInstallJvm.headerComment)
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

            println(message)
          }
        else
          Task.point(())
      }

    } yield ()

  def run(options: JavaHomeOptions, args: RemainingArgs): Unit = {

    val params = JavaHomeParams(options).exitOnError()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val coursierCache = params.cache.cache(pool, logger)

    val javaHome = params.shared.javaHome(coursierCache, params.output.verbosity)
    val task = javaHome.getWithRetainedId(params.shared.id)

    logger.init()
    val (retainedId, home) =
      try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
      catch {
        case e: JvmCache.JvmCacheException if params.output.verbosity <= 1 =>
          System.err.println(e.getMessage)
          sys.exit(1)
      }
      finally logger.stop()

    lazy val envUpdate = javaHome.environmentFor(retainedId, home)
    if (params.env.env)
      println(envUpdate.script)
    else if (params.env.setup) {
      val setupTask = setup(
        envUpdate,
        params.env.envVarUpdater,
        params.output.verbosity
      )
      setupTask.unsafeRun()(coursierCache.ec)
    } else
      println(home.getAbsolutePath)
  }
}
