package coursier.cache

import coursier.cache.internal.MockCacheEscape
import coursier.core.Repository
import coursier.util.{EitherT, Task}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

final case class MockCache(base: String) extends Cache[Task] {

  implicit def ec: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private lazy val fs = g.require("fs")

  private def textResource0(path: String)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()

    fs.readFile(path, "utf-8", {
      (err: js.Dynamic, data: js.Dynamic) =>
        if (js.isUndefined(err) || err == null) p.success(data.asInstanceOf[String])
        else p.failure(new Exception(err.toString))
        ()
    }: js.Function2[js.Dynamic, js.Dynamic, Unit])

    p.future
  }

  def fetch: Repository.Fetch[Task] = { artifact =>
    EitherT {
      assert(artifact.authentication.isEmpty)

      val path = base + "/" + MockCacheEscape.urlAsPath(artifact.url)

      Task { implicit ec =>
        textResource0(path)
          .map(Right(_))
          .recoverWith {
            case e: Exception =>
              Future.successful(Left(e.getMessage))
          }
      }
    }
  }

  def fetchs: Seq[Repository.Fetch[Task]] =
    Seq(fetch)

}

object MockCache {

  def create(base: String): MockCache =
    MockCache(base)

}
