package coursier.cli.jvm

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs

import java.io.File

import coursier.jvm.JvmCacheLogger
import coursier.util.Sync

object JavaHome extends CaseApp[JavaHomeOptions] {
  def run(options: JavaHomeOptions, args: RemainingArgs): Unit = {

    val params = JavaHomeParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
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
        home <- handle.get(params.shared.id)
      } yield home

    logger.init()
    val home =
      try task.unsafeRun()(coursierCache.ec) // TODO Better error messages for relevant exceptions
      finally logger.stop()

    println(home.getAbsolutePath)
  }
}
