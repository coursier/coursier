package coursier.cli.jvm

import java.io.File

import caseapp.core.RemainingArgs
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.setup.MaybeInstallJvm
import coursier.cli.Util.ValidatedExitOnError
import coursier.env.{EnvironmentUpdate, EnvVarUpdater, ProfileUpdater, WindowsEnvVarUpdater}
import coursier.jvm.{JvmCache, JvmCacheLogger}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

object JavaHome extends CoursierCommand[JavaHomeOptions] {

  override def group: String = CommandGroup.java

  def run(options: JavaHomeOptions, args: RemainingArgs): Unit = {

    val params = JavaHomeParams(options).exitOnError()

    val pool                  = Sync.fixedThreadPool(params.cache.parallel)
    val logger                = params.output.logger()
    val coursierCache         = params.cache.cache(pool, logger)
    val noUpdateCoursierCache = params.cache.cache(pool, logger, overrideTtl = Some(Duration.Inf))

    val (jvmCache, javaHome) = params.shared.cacheAndHome(
      coursierCache,
      noUpdateCoursierCache,
      params.repository.repositories,
      params.output.verbosity
    )
    val task = javaHome.getWithIsSystem(params.shared.id)

    val (isSystem, home) = logger.use {
      try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
      catch {
        case e: JvmCache.JvmCacheException if params.output.verbosity <= 1 =>
          System.err.println(e.getMessage)
          sys.exit(1)
      }
    }

    lazy val envUpdate = javaHome.environmentFor(isSystem, home)
    if (params.env.env) {
      val script =
        if (params.env.windowsScript)
          coursier.jvm.JavaHome.finalBatScript(envUpdate)
        else
          coursier.jvm.JavaHome.finalBashScript(envUpdate)
      print(script)
    }
    else if (params.env.disableEnv) {
      val script =
        if (params.env.windowsScript)
          coursier.jvm.JavaHome.disableBatScript()
        else
          coursier.jvm.JavaHome.disableBashScript()
      print(script)
    }
    else if (params.env.setup) {
      val setupTask = params.env.setupTask(
        envUpdate,
        params.env.envVarUpdater,
        params.output.verbosity,
        MaybeInstallJvm.headerComment
      )
      setupTask.unsafeRun()(coursierCache.ec)
    }
    else
      println(home.getAbsolutePath)
  }
}
