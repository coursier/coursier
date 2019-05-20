package coursier.cache

import coursier.cache.internal.{MockCacheEscape, Platform}
import coursier.core.Repository
import coursier.util.{EitherT, Task}

import scala.concurrent.{ExecutionContext, Future}

final case class MockCache(base: String) extends Cache[Task] {

  implicit def ec: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def fetch: Repository.Fetch[Task] = { artifact =>
    EitherT {
      assert(artifact.authentication.isEmpty)

      val path =
        if (artifact.url.startsWith("file:")) {
          val path = artifact
            .url
            .stripPrefix("file:")
            .dropWhile(_ == '/')
          if (path.endsWith("/"))
            path + ".directory"
          else
            path
        } else
          base + "/" + MockCacheEscape.urlAsPath(artifact.url)

      Task { implicit ec =>
        Platform.textResource(path)
          .map(Right(_))
          .recoverWith {
            case e: Exception =>
              Future.successful(Left(e.getMessage))
          }
      }
    }
  }

}

object MockCache {

  def create(base: String): MockCache =
    MockCache(base)

}
