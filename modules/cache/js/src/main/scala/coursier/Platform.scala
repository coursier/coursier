package coursier

import coursier.util.{EitherT, Task}
import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.scalajs.js.timers._

object Platform {

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  val timeout = 4000

  /** Available if we're running on node, and package xhr2 is installed */
  lazy val xhr =
    if (js.isUndefined(g.XMLHttpRequest))
      g.require("xhr2")
    else
      g.XMLHttpRequest
  def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(timeout) {
      if (!p.isCompleted) {
        p.failure(new Exception(s"Timeout when fetching $target"))
      }
    }

  def get(url: String)(implicit executionContext: ExecutionContext): Future[String] = {
    val p = Promise[String]()
    val xhrReq0 = xhrReq()
    val f = { _: Event =>
      p.success(xhrReq0.responseText)
    }
    xhrReq0.onload = f

    xhrReq0.open("GET", "https://jsonp.afeld.me/?url=" + url) // escaping…
    xhrReq0.send()

    fetchTimeout(url, p)
    p.future
  }

  val artifact: Repository.Fetch[Task] = { artifact =>
    EitherT(
      Task { implicit ec =>
        get(artifact.url)
          .map(Right(_))
          .recover { case e: Exception =>
          Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")"))
        }
      }
    )
  }

  def fetch(
    repositories: Seq[core.Repository]
  ): ResolutionProcess.Fetch[Task] =
    ResolutionProcess.fetch(repositories, Platform.artifact)

}
