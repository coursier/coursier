package coursier.cache.internal

import coursier.util.WebPage
import org.scalajs.dom.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.timers._

object Platform {

  private def timeout = 4000

  /** Available if we're running on node, and package xhr2 is installed */
  private lazy val (xhr, fromBrowser) =
    if (js.typeOf(g.XMLHttpRequest) == "undefined")
      (g.require("xhr2"), false)
    else
      (g.XMLHttpRequest, true)
  private def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  private def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(timeout) {
      if (!p.isCompleted)
        p.failure(new Exception(s"Timeout when fetching $target"))
    }

  private lazy val fs = g.require("fs")

  // on node and from the browser
  def get(url: String)(implicit executionContext: ExecutionContext): Future[String] = {
    val p       = Promise[String]()
    val xhrReq0 = xhrReq()
    val f = { _: Event =>
      if (xhrReq0.status >= 200 && xhrReq0.status < 300)
        p.success(xhrReq0.responseText)
      else
        p.failure(
          new Exception(
            url + System.lineSeparator() +
              s"Status: ${xhrReq0.status}" + System.lineSeparator() +
              xhrReq0.responseText
          )
        )
    }
    xhrReq0.onload = f

    val url0 =
      if (fromBrowser)
        "https://jsonp.afeld.me/?url=" + url
      else
        url
    xhrReq0.open("GET", url0) // escaping…
    xhrReq0.send()

    fetchTimeout(url, p)
    p.future
  }

  // only on node
  def textResource(
    path: String,
    linkUrlOpt: Option[String] = None
  )(implicit
    ec: ExecutionContext
  ): Future[String] = {
    val p = Promise[String]()

    val cb: js.Function2[js.Dynamic, js.Dynamic, Unit] =
      (err, data) => {
        if (js.typeOf(err) == "undefined" || err == null) {
          val s = data.asInstanceOf[String]
          val res = linkUrlOpt match {
            case None => s
            case Some(url) =>
              WebPage.listElements(url, s)
                .mkString("\n")
          }
          p.success(res)
        }
        else
          p.failure(new Exception(err.toString))
        ()
      }

    fs.readFile(path, "utf-8", cb)

    p.future
  }

}
