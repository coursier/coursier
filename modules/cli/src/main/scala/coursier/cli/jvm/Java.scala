package coursier.cli.jvm

import java.io.File

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.params.EnvParams
import coursier.cli.setup.MaybeInstallJvm
import coursier.cli.Util.ValidatedExitOnError
import coursier.core.Version
import coursier.jvm.{Execve, JvmCache, JvmCacheLogger}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

object Java extends CaseApp[JavaOptions] {
  override def stopAtFirstUnrecognized = true
  def run(options: JavaOptions, args: RemainingArgs): Unit = {

    // that should probably be fixed by case-app
    val args0 =
      if (args.remaining.headOption.contains("--"))
        args.all.drop(1)
      else
        args.all

    val csJavaFailVariable = coursier.jvm.JavaHome.csJavaFailVariable
    if (Option(System.getenv(csJavaFailVariable)).nonEmpty) {
      System.err.println(s"$csJavaFailVariable is set, refusing to do anything.")
      sys.exit(1)
    }

    val params = JavaParams(options, args0.nonEmpty).exitOnError()

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val coursierCache = params.cache.cache(pool, logger)
    val noUpdateCoursierCache = params.cache.cache(pool, logger, overrideTtl = Some(Duration.Inf))

    val (jvmCache, javaHome) = params.shared.cacheAndHome(coursierCache, noUpdateCoursierCache, params.output.verbosity)

    if (params.installed) {
      val task =
        for {
          list <- jvmCache.installed()
          _ <- Task.delay {
            for (id <- list)
              // ':' more readable than '@'
              System.out.println(id.replaceFirst("@", ":"))
          }
        } yield ()
      task.unsafeRun()(coursierCache.ec)
    } else if (params.available) {
      val task =
        for {
          index <- jvmCache.index.getOrElse(sys.error("should not happen"))
          maybeError <- Task.delay {
            index.available().map { map =>
              val available = for {
                (name, versionMap)  <- map.toVector.sortBy(_._1)
                version <- versionMap.keysIterator.toVector.map(Version(_)).sorted.map(_.repr)
              } yield s"$name:$version"
              for (id <- available)
                System.out.println(id)
            }
          }
        } yield maybeError

      val maybeError = task.unsafeRun()(coursierCache.ec)
      maybeError match {
        case Left(error) =>
          System.err.println(error)
          sys.exit(1)
        case Right(()) =>
      }
    } else {

      val task = javaHome.getWithRetainedId(params.shared.id)

      // TODO More thin grain handling of the logger lifetime here.
      // As is, its output gets flushed too late sometimes, resulting in progress bars
      // displayed after actions done after downloads.
      logger.init()
      val (retainedId, home) =
        try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
        catch {
          case e: JvmCache.JvmCacheException if params.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
        }
        finally logger.stop()

      val envUpdate = javaHome.environmentFor(retainedId, home)

      val javaBin = {

        val javaBin0 = new File(home, "bin/java")

        def notFound(): Nothing = {
          System.err.println(s"Error: $javaBin0 not found")
          sys.exit(1)
        }

        // should we use isFile instead of exists?
        if (Windows.isWindows)
          Windows.pathExtensions
            .map(ext => new File(home, s"bin/java$ext"))
            .filter(_.exists())
            .headOption
            .getOrElse(notFound())
        else
          Some(javaBin0)
            .filter(_.exists())
            .getOrElse(notFound())
      }

      if (!javaBin.isFile) {
        System.err.println(s"Error: $javaBin not found")
        sys.exit(1)
      }

      if (params.env.env) {
        val script = coursier.jvm.JavaHome.finalScript(envUpdate, jvmCache.baseDirectory.toPath)
        print(script)
      } else if (params.env.disableEnv) {
        val script = coursier.jvm.JavaHome.disableScript(jvmCache.baseDirectory.toPath)
        print(script)
      } else if (params.env.setup) {
        val task = params.env.setupTask(
          envUpdate,
          params.env.envVarUpdater,
          params.output.verbosity,
          MaybeInstallJvm.headerComment
        )
        task.unsafeRun()(coursierCache.ec)
      } else if (Execve.available()) {
        val extraEnv = envUpdate.transientUpdates()
        val fullEnv = (sys.env ++ extraEnv)
          .iterator
          .map {
            case (k, v) =>
              s"$k=$v"
          }
          .toArray
          .sorted
        Execve.execve(javaBin.getAbsolutePath, (javaBin.getAbsolutePath +: args0).toArray, fullEnv)
        System.err.println("should not happen")
        sys.exit(1)
      } else {
        val extraEnv = envUpdate.transientUpdates()
        val b = new ProcessBuilder((javaBin.getAbsolutePath +: args0): _*)
        b.inheritIO()
        val env = b.environment()
        for ((k, v) <- extraEnv)
          env.put(k, v)
        val p = b.start()
        val retCode = p.waitFor()
        sys.exit(retCode)
      }
    }
  }
}
