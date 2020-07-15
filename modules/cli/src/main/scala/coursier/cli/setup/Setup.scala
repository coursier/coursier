package coursier.cli.setup

import java.io.File
import java.util.Locale

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.env.{EnvironmentUpdate, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.install.{Channels, InstallDir}
import coursier.jvm.JvmCache
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

object Setup extends CaseApp[SetupOptions] {

  def run(options: SetupOptions, args: RemainingArgs): Unit = {

    val params = SetupParams(options).exitOnError()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val cache = params.cache.cache(pool, logger)
    val noUpdateCoursierCache = params.cache.cache(pool, params.output.logger(), overrideTtl = Some(Duration.Inf))

    val javaHome = params.sharedJava.javaHome(cache, noUpdateCoursierCache, params.output.verbosity)

    val envVarUpdaterOpt =
      if (params.env.env) None
      else Some(params.env.envVarUpdater)

    val graalvmHome = { version: String =>
      javaHome.get(s"graalvm:$version")
    }

    val installCache = cache.withLogger(params.output.logger(byFileType = true))
    val installDir = params.sharedInstall.installDir(installCache)
      .withVerbosity(params.output.verbosity)
      .withNativeImageJavaHome(Some(graalvmHome))
    val channels = Channels(params.sharedChannel.channels, params.sharedInstall.repositories, installCache)
      .withVerbosity(params.output.verbosity)

    val confirm =
      if (params.yes)
        Confirm.YesToAll()
      else
        Confirm.ConsoleInput().withIndent(2)

    val tasks = Seq(
      MaybeInstallJvm(
        cache,
        envVarUpdaterOpt,
        javaHome,
        confirm,
        params.sharedJava.id
      ),
      MaybeSetupPath(
        installDir,
        envVarUpdaterOpt,
        EnvironmentUpdate.defaultGetEnv,
        File.pathSeparator,
        confirm
      ),
      MaybeInstallApps(installDir, channels, params.apps)
    )

    val init =
      if (params.tryRevert) {
        val message = "Warning: the --try-revert option is experimental. Keep going only if you know what you are doing."
        confirm.confirm(message, default = false)
      } else
        Task.point(())
    val task = tasks.foldLeft(init) { (acc, step) =>
      val t = if (params.tryRevert) step.tryRevert else step.fullTask(System.err)
      acc.flatMap(_ => t)
    }

    if (params.banner && !params.tryRevert)
      // from https://github.com/scala/scala/blob/eb1ea8b367f9b240afc0b16184396fa3bbf7e37c/project/VersionUtil.scala#L34-L39
      System.err.println(
        """
          |     ________ ___   / /  ___
          |    / __/ __// _ | / /  / _ |
          |  __\ \/ /__/ __ |/ /__/ __ |
          | /____/\___/_/ |_/____/_/ | |
          |                          |/
          |""".stripMargin
      )

    // TODO Better error messages for relevant exceptions
    try task.unsafeRun()(cache.ec)
    catch {
      case e: InstallDir.InstallDirException if params.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case e: JvmCache.JvmCacheException if params.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
    }
  }
}
