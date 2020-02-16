package coursier.cli.jvm

import java.io.File

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.core.Version
import coursier.jvm.{Execve, JvmCache, JvmCacheLogger}
import coursier.launcher.internal.Windows
import coursier.util.{Sync, Task}

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

    val params = JavaParams(options).exitOnError()

    if (Seq(params.env.env, params.installed, params.available).count(identity) > 1) {
      System.err.println("Error: can only specify one of --env, --installed, --available.")
      sys.exit(1)
    }

    if ((params.env.env || params.installed || params.available) && args0.nonEmpty) {
      System.err.println(s"Error: unexpected arguments passed along --env, --installed, or --available: ${args0.mkString(" ")}")
      sys.exit(1)
    }

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val coursierCache = params.cache.cache(pool, logger)

    val (jvmCache, javaHome) = params.shared.cacheAndHome(coursierCache, params.output.verbosity)

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

      val task =
        for {
          homeId <- javaHome.getWithRetainedId(params.shared.id)
          (id, home) = homeId
          envUpdate = javaHome.environmentFor(id, home)
        } yield (id, home, envUpdate)

      // TODO More thin grain handling of the logger lifetime here.
      // As is, its output gets flushed too late sometimes, resulting in progress bars
      // displayed after actions done after downloads.
      logger.init()
      val (retainedId, home, envUpdate) =
        try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
        catch {
          case e: JvmCache.JvmCacheException if params.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
        }
        finally logger.stop()

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

      val extraEnv = envUpdate.scriptUpdates()

      if (params.env.env) {
        val q = "\""
        for ((k, v) <- extraEnv)
          // FIXME Is this escaping fine?
          println(s"export $k=$q${v.replaceAllLiterally(q, "\\" + q)}$q")
      } else if (Execve.available()) {
        val fullEnv = (sys.env ++ extraEnv)
          .toArray
          .map {
            case (k, v) =>
              s"$k=$v"
          }
          .sorted
        Execve.execve(javaBin.getAbsolutePath, (javaBin.getAbsolutePath +: args0).toArray, fullEnv)
        System.err.println("should not happen")
        sys.exit(1)
      } else {
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
