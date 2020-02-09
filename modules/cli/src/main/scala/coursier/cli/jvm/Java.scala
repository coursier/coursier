package coursier.cli.jvm

import java.io.File

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import coursier.jvm.{Execve, JvmCacheLogger}
import coursier.util.Sync
import coursier.jvm.JvmCache
import coursier.launcher.internal.Windows

object Java extends CaseApp[JavaOptions] {
  override def stopAtFirstUnrecognized = true
  def run(options: JavaOptions, args: RemainingArgs): Unit = {

    val params = JavaParams(options).exitOnError()

    if (params.env && args.all.nonEmpty) {
      System.err.println(s"Error: unexpected arguments passed along --env: ${args.all.mkString(" ")}")
      sys.exit(1)
    }

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val logger = params.output.logger()
    val coursierCache = params.cache.cache(pool, logger)

    val task =
      for {
        baseHandle <- coursier.jvm.JavaHome.default
        handle = baseHandle
          .withJvmCacheLogger(params.shared.jvmCacheLogger(params.output.verbosity))
          .withCoursierCache(coursierCache)
        homeId <- handle.getWithRetainedId(params.shared.id)
        (id, home) = homeId
        envUpdate = handle.environmentFor(id, home)
      } yield (id, home, envUpdate)

    // TODO More thin grain handling of the logger lifetime here.
    // As is, its output gets flushed too late sometimes, resulting in progress bars
    // displayed after actions done after downloads.
    logger.init()
    val (retainedId, home, envUpdate) =
      try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
      finally logger.stop()

    val javaBin = {

      val javaBin0 = new File(home, "bin/java")

      def notFound(): Nothing = {
        System.err.println(s"Error: $javaBin0 not found")
        sys.exit(1)
      }

      // should we use isFile instead of exists?
      if (Windows.isWindows)
        // should we really add the empty extension?
        (Stream("") ++ Windows.pathExtensions)
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

    val extraEnv = envUpdate.updatedEnv()

    if (params.env) {
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
      Execve.execve(javaBin.getAbsolutePath, (javaBin.getAbsolutePath +: args.all).toArray, fullEnv)
      System.err.println("should not happen")
      sys.exit(1)
    } else {
      val b = new ProcessBuilder((javaBin.getAbsolutePath +: args.all): _*)
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
