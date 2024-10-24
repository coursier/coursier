package coursier.cache

import coursier.cache.internal.{MockCacheEscape, Platform}
import coursier.util.{EitherT, Task}
import dataclass.data

import scala.concurrent.{ExecutionContext, Future}

@data class MockCache(base: String) extends Cache[Task] {

  implicit def ec: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def fetch: Cache.Fetch[Task] = { artifact =>
    EitherT {
      assert(artifact.authentication.isEmpty)

      val (artifact0, links) =
        if (artifact.url.endsWith("/.links"))
          (artifact.withUrl(artifact.url.stripSuffix(".links")), true)
        else
          (artifact, false)

      val path =
        if (artifact0.url.startsWith("file:/")) {
          val path = artifact0
            .url
            .stripPrefix(if (artifact0.url.startsWith("file:///")) "file://" else "file:")
          if (path.endsWith("/"))
            path + ".directory"
          else
            path
        }
        else
          base + "/" + MockCacheEscape.urlAsPath(artifact0.url)

      Task { implicit ec =>
        Platform.textResource(path, if (links) Some(artifact0.url) else None)
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
