package coursier.util

import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

private[coursier] abstract class WebPageCompatibility {

  // FIXME Won't work in the browser
  lazy val cheerio = g.require("cheerio")

  lazy val jqueryAvailable = js.typeOf(g.$) != "undefined"

  def listWebPageRawElements(page: String): Iterator[String] = {

    val links = new ListBuffer[String]

    // getting weird "maybe a wrong Dynamic method signature" errors when trying to factor that more

    if (jqueryAvailable)
        g.$("<div></div>").html(page).find("a").each({ self: js.Dynamic =>
        val href = g.$(self).attr("href")
        if (js.typeOf(href) != "undefined")
          links += href.asInstanceOf[String]
        ()
      }: js.ThisFunction0[js.Dynamic, Unit])
    else {
      val jquery = cheerio.load(page)
      jquery("a").each({ self: js.Dynamic =>
        val href = jquery(self).attr("href")
        if (js.typeOf(href) != "undefined")
          links += href.asInstanceOf[String]
        ()
      }: js.ThisFunction0[js.Dynamic, Unit])
    }

    links.result().iterator
  }

}
