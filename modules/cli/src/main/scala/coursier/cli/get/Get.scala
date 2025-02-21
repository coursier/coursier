package coursier.cli.get

import caseapp.core.RemainingArgs
import coursier.cache.ArchiveCache
import coursier.cli.CoursierCommand
import coursier.util.{Artifact, Sync, Task}

import scala.concurrent.ExecutionContext

object Get extends CoursierCommand[GetOptions] {
  override def hidden: Boolean = true
  def run(options: GetOptions, args: RemainingArgs): Unit = {

    val params = GetParams(options).toEither match {
      case Left(errors) =>
        for (e <- errors.toList)
          System.err.println(e)
        sys.exit(1)
      case Right(p) => p
    }

    val pool  = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())

    val archiveCache = ArchiveCache[Task](params.archiveCacheLocation)
      .withCache(cache)

    val artifacts = args.all.map { rawUrl =>
      val artifact = Artifact.fromUrl(rawUrl)
      params.changing match {
        case None           => artifact
        case Some(changing) => artifact.withChanging(changing)
      }
    }

    if (artifacts.isEmpty)
      System.err.println("Warning: no URL passed")

    var anyError = false

    val fetchAll =
      artifacts.map { artifact =>
        if (options.archive)
          archiveCache.get(artifact)
        else
          cache.file(artifact).run
      }

    val initLogger = Task.delay(cache.logger.init())
    val stopLogger = Task.delay(cache.logger.stop())

    val task =
      for {
        _             <- initLogger
        a             <- Task.gather.gather(fetchAll).attempt
        _             <- stopLogger
        pathsOrErrors <- Task.fromEither(a)
      } yield {
        val errorsIt = pathsOrErrors.iterator.collect { case Left(e) => e }
        anyError = errorsIt.hasNext
        if (!anyError || params.force) {
          val pathsIt = pathsOrErrors.iterator.collect { case Right(p) => p }
          val output  = pathsIt.mkString(params.separator)
          println(output)
        }
        for (err <- errorsIt)
          if (params.output.verbosity == 0)
            System.err.println(err.getMessage)
          else if (params.output.verbosity >= 1)
            throw err
      }

    val ec = ExecutionContext.fromExecutorService(pool)
    task.unsafeRun()(ec)

    if (anyError)
      sys.exit(1)
  }
}
