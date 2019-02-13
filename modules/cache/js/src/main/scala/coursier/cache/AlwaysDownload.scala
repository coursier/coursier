package coursier.cache
import coursier.Platform
import coursier.core.Repository
import coursier.util.{EitherT, Task}

import scala.concurrent.{ExecutionContext, Future}

final case class AlwaysDownload(
  logger: CacheLogger = CacheLogger.nop
) extends Cache[Task] {

  def fetch: Repository.Fetch[Task] = { artifact =>
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

  def fetchs: Seq[Repository.Fetch[Task]] = Seq(fetch)

  def ec: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

}
