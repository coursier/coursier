package coursier.cli.jvm

import java.io.File

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.jvm.{Execve, JvmCacheLogger}
import coursier.util.Sync
import coursier.jvm.JvmCache

object Java extends CaseApp[JavaOptions] {
  override def stopAtFirstUnrecognized = true
  def run(options: JavaOptions, args: RemainingArgs): Unit = {

    val params = JavaParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }

    if (params.env && args.all.nonEmpty) {
      System.err.println(s"Error: unexpected arguments passed along --env: ${args.all.mkString(" ")}")
      sys.exit(1)
    }

    val pool = Sync.fixedThreadPool(params.shared.cache.parallel)
    val logger = params.shared.output.logger()
    val coursierCache = params.shared.cache.cache(pool, logger)

    val task =
      for {
        baseHandle <- coursier.jvm.JavaHome.default
        handle = baseHandle
          .withJvmCacheLogger(params.shared.jvmCacheLogger)
          .withCoursierCache(coursierCache)
        homeId <- handle.getWithRetainedId(params.shared.id)
      } yield homeId

    logger.init()
    val (retainedId, home) =
      try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
      finally logger.stop()

    // TODO Extension on Windows
    val javaBin = new File(home, "bin/java")

    if (!javaBin.isFile) {
      System.err.println(s"Error: $javaBin not found")
      sys.exit(1)
    }

    val extraEnv =
      if (retainedId == coursier.jvm.JavaHome.systemId)
        Nil
      else {
        val addPath = !JvmCache.isMacOs // /usr/bin/java reads JAVA_HOME on macOS, no need to update the PATH
        val pathEnv =
          if (addPath) {
            val binDir = new File(home, "bin").getAbsolutePath
            val updatedPath = binDir + Option(System.getenv("PATH")).map(File.pathSeparator + _).getOrElse("")
            Seq("PATH" -> updatedPath)
          } else
            Nil
        Seq("JAVA_HOME" -> home.getAbsolutePath) ++
          pathEnv
      }

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
