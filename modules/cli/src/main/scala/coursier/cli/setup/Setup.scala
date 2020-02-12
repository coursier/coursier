package coursier.cli.setup

import java.io.File
import java.util.Locale

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.install.{Channels, InstallDir}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

object Setup extends CaseApp[SetupOptions] {

  def run(options: SetupOptions, args: RemainingArgs): Unit = {

    val params = SetupParams(options).exitOnError()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val cache = params.cache.cache(pool, logger)

    val javaHome = params.sharedJava.javaHome(cache, params.output.verbosity)

    val envVarUpdater =
      if (Windows.isWindows)
        Left(WindowsEnvVarUpdater())
      else
        Right(
          ProfileUpdater()
            .withHome(params.homeOpt.orElse(ProfileUpdater.defaultHome))
        )

    val installDir = InstallDir(params.sharedInstall.dir, cache)
      .withVerbosity(params.output.verbosity)
      .withGraalvmParamsOpt(params.sharedInstall.graalvmParamsOpt)
      .withCoursierRepositories(params.sharedInstall.repositories)

    val channels = Channels(params.sharedChannel.channels, params.sharedInstall.repositories, cache)
      .withVerbosity(params.output.verbosity)

    val confirm =
      if (params.yes)
        Confirm.yesToAll()
      else
        Confirm.default

    val tasks = Seq(
      MaybeInstallJvm(cache, envVarUpdater, javaHome, confirm),
      MaybeSetupPath(
        installDir,
        envVarUpdater,
        EnvironmentUpdate.defaultGetEnv,
        File.pathSeparator,
        confirm
      ),
      MaybeInstallApps(installDir, channels, DefaultAppList.defaultAppList)
    )

    val task = tasks.foldLeft(Task.point(()))((acc, t) => acc.flatMap(_ => t.fullTask(System.out)))

    if (Option(System.getenv("CS_SETUP_BANNER")).map(_.toLowerCase(Locale.ROOT)).contains("true"))
      System.out.println(
        """   ____      _      _   _     _   _   U _____ u   ____
          |U | __")uU  /"\  u | \ |"|   | \ |"|  \| ___"|/U |  _"\ u
          | \|  _ \/ \/ _ \/ <|  \| |> <|  \| |>  |  _|"   \| |_) |/
          |  | |_) | / ___ \ U| |\  |u U| |\  |u  | |___    |  _ <
          |  |____/ /_/   \_\ |_| \_|   |_| \_|   |_____|   |_| \_\
          | _|| \\_  \\    >> ||   \\,-.||   \\,-.<<   >>   //   \\_
          |(__) (__)(__)  (__)(_")  (_/ (_")  (_/(__) (__) (__)  (__)
          |""".stripMargin
      )

    // TODO Better error messages for relevant exceptions
    task.unsafeRun()(cache.ec)
  }
}
