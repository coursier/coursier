package coursier.cli.cat

import caseapp.core.RemainingArgs
import coursier.cache.ArchiveCache
import coursier.cli.CoursierCommand
import coursier.util.{Artifact, Sync, Task}

import java.nio.file.Files

import scala.concurrent.ExecutionContext
import scala.util.Using

object Cat extends CoursierCommand[CatOptions] {
  override def hidden: Boolean = true
  def run(options: CatOptions, args: RemainingArgs): Unit = {

    val params = CatParams(options).toEither match {
      case Left(errors) =>
        for (e <- errors.toList)
          System.err.println(e)
        sys.exit(1)
      case Right(p) => p
    }

    val pool  = Sync.fixedThreadPool(params.cache.parallel)
    val cache = params.cache.cache(pool, params.output.logger())

    val rawUrl = args.all match {
      case Seq() =>
        System.err.println("No URL passed")
        sys.exit(1)
      case Seq(arg) =>
        arg
      case _ =>
        System.err.println("Too many arguments passed, expected one")
        sys.exit(1)
    }

    val artifact = {
      val artifact0 = Artifact.fromUrl(rawUrl)
      params.changing match {
        case None           => artifact0
        case Some(changing) => artifact0.withChanging(changing)
      }
    }

    var anyError = false

    val fetch = cache.file(artifact).run

    val initLogger = Task.delay(cache.logger.init())
    val stopLogger = Task.delay(cache.logger.stop())

    val task =
      for {
        _           <- initLogger
        a           <- fetch.attempt
        _           <- stopLogger
        pathOrError <- Task.fromEither(a)
      } yield pathOrError match {
        case Left(err) =>
          anyError = true
          if (params.output.verbosity == 0)
            System.err.println(err.getMessage)
          else if (params.output.verbosity >= 1)
            throw err
        case Right(path) =>
          Using.resource(Files.newInputStream(path.toPath)) { is =>
            val b    = Array.ofDim[Byte](128 * 1024)
            var read = 0
            while ({
              read = is.read(b)
              read >= 0
            })
              System.out.write(b, 0, read)
          }
      }

    val ec = ExecutionContext.fromExecutorService(pool)
    task.unsafeRun()(ec)

    if (anyError)
      sys.exit(1)
  }
}
