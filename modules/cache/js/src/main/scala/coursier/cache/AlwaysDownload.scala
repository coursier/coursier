package coursier.cache

import coursier.cache.internal.Platform
import coursier.util.{EitherT, Task}
import dataclass.data

import scala.concurrent.{ExecutionContext, Future}

@data class AlwaysDownload(
  logger: CacheLogger = CacheLogger.nop
) extends Cache[Task] {

  def fetch: Cache.Fetch[Task] = { artifact =>
    EitherT(
      Task { implicit ec =>
        Future(logger.downloadingArtifact(artifact.url))
          .flatMap(_ => Platform.get(artifact.url))
          .map { s => logger.downloadedArtifact(artifact.url, success = true); Right(s) }
          .recover { case e: Exception =>
            val msg = e.toString + Option(e.getMessage).fold("")(" (" + _ + ")")
            logger.downloadedArtifact(artifact.url, success = false)
            Left(msg)
          }
      }
    )
  }

  def ec: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

}
