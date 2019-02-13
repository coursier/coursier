package coursier.cache.internal

import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.scalajs.js.timers._

object Platform {

  private def timeout = 4000

  /** Available if we're running on node, and package xhr2 is installed */
  private lazy val (xhr, fromBrowser) =
    if (js.isUndefined(g.XMLHttpRequest))
      (g.require("xhr2"), false)
    else
      (g.XMLHttpRequest, true)
  private def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  private def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(timeout) {
      if (!p.isCompleted) {
        p.failure(new Exception(s"Timeout when fetching $target"))
      }
    }

  private lazy val fs = g.require("fs")


  // on node and from the browser
  def get(url: String)(implicit executionContext: ExecutionContext): Future[String] = {
    val p = Promise[String]()
    val xhrReq0 = xhrReq()
    val f = { _: Event =>
      p.success(xhrReq0.responseText)
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
  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()

    fs.readFile(path, "utf-8", {
      (err: js.Dynamic, data: js.Dynamic) =>
        if (js.isUndefined(err) || err == null) p.success(data.asInstanceOf[String])
        else p.failure(new Exception(err.toString))
        ()
    }: js.Function2[js.Dynamic, js.Dynamic, Unit])

    p.future
  }

}
