package coursier.core

import scala.scalajs.js
import js.Dynamic.{ global => g }
import org.scalajs.dom.raw.NodeList

import coursier.util.{SaxHandler, Xml}

import scala.collection.mutable.ListBuffer

package object compatibility {
  def option[A](a: js.Dynamic): Option[A] =
    if (js.isUndefined(a)) None
    else Some(a.asInstanceOf[A])
  def dynOption(a: js.Dynamic): Option[js.Dynamic] =
    if (js.isUndefined(a)) None
    else Some(a)

  private def between(c: Char, lower: Char, upper: Char) = lower <= c && c <= upper

  implicit class RichChar(val c: Char) extends AnyVal {
    def letterOrDigit: Boolean = {
      between(c, '0', '9') || letter
    }
    def letter: Boolean = between(c, 'a', 'z') || between(c, 'A', 'Z')
  }

  def newFromXmlDomOrGlobal(name: String) = {
    var defn = g.selectDynamic(name)
    if (js.isUndefined(defn))
      defn = g.require("xmldom").selectDynamic(name)

    js.Dynamic.newInstance(defn)()
  }

  lazy val DOMParser = newFromXmlDomOrGlobal("DOMParser")
  lazy val XMLSerializer = newFromXmlDomOrGlobal("XMLSerializer")
  lazy val sax =
    if (js.isUndefined(g.sax))
      g.require("sax")
    else
      g.sax

  // Can't find these from node
  val ELEMENT_NODE = 1 // org.scalajs.dom.raw.Node.ELEMENT_NODE
  val TEXT_NODE = 3 // org.scalajs.dom.raw.Node.TEXT_NODE

  def fromNode(node: org.scalajs.dom.raw.Node): Xml.Node = {

    val node0 = node.asInstanceOf[js.Dynamic]

    new Xml.Node {
      def label =
        option[String](node0.nodeName)
          .getOrElse("")
      def children =
        option[NodeList](node0.childNodes)
          .map(l => List.tabulate(l.length)(l.item).map(fromNode))
          .getOrElse(Nil)

      def attributes = {
        val attr = node.attributes
        (0 until attr.length)
          .map(idx => attr(idx))
          .map { a =>
            (Option(a.prefix).getOrElse(""), a.name, a.value)
          }
      }

      def isText =
        option[Int](node0.nodeType)
          .contains(TEXT_NODE)
      def textContent =
        option(node0.textContent)
          .getOrElse("")
      def isElement =
        option[Int](node0.nodeType)
          .contains(ELEMENT_NODE)

      override def toString =
        XMLSerializer.serializeToString(node).asInstanceOf[String]
    }
  }


  def xmlParseSax(str: String, handler: SaxHandler): handler.type = {

    val parser = sax.parser(true)

    parser.onerror = { (e: js.Dynamic) =>
      ???
    }: js.Function1[js.Dynamic, Unit]
    parser.ontext = { (t: String) =>
      ???
    }: js.Function1[String, Unit]
    parser.onopentag = { (node: js.Dynamic) =>
      handler.startElement(node.name.asInstanceOf[String])
    }: js.Function1[js.Dynamic, Unit]
    parser.ontext = { (t: String) =>
      val a = t.toCharArray
      handler.characters(a, 0, a.length)
    }: js.Function1[String, Unit]
    parser.onclosetag = { (tagName: String) =>
      handler.endElement(tagName)
    }: js.Function1[String, Unit]

    parser.write(str).close()

    handler
  }

  def xmlParseDom(s: String): Either[String, Xml.Node] = {
    val doc = {
      if (s.isEmpty) None
      else {
        for {
          xmlDoc <- dynOption(DOMParser.parseFromString(s, "text/xml"))
          rootNodes <- dynOption(xmlDoc.childNodes)
          // From node, rootNodes.head is sometimes just a comment instead of the main root node
          // (tested with org.ow2.asm:asm-commons in CentralTests)
          rootNode <- rootNodes.asInstanceOf[js.Array[js.Dynamic]]
            .flatMap(option[org.scalajs.dom.raw.Node])
            .dropWhile(_.nodeType != ELEMENT_NODE)
            .headOption
        } yield rootNode
      }
    }

    Right(doc.fold(Xml.Node.empty)(fromNode))
  }

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  // FIXME Won't work in the browser
  lazy val cheerio = g.require("cheerio")

  lazy val jqueryAvailable = !js.isUndefined(g.$)

  def listWebPageRawElements(page: String): Iterator[String] = {

    val links = new ListBuffer[String]

    // getting weird "maybe a wrong Dynamic method signature" errors when trying to factor that more

    if (jqueryAvailable)
        g.$("<div></div>").html(page).find("a").each({ self: js.Dynamic =>
        val href = g.$(self).attr("href")
        if (!js.isUndefined(href))
          links += href.asInstanceOf[String]
        ()
      }: js.ThisFunction0[js.Dynamic, Unit])
    else {
      val jquery = cheerio.load(page)
      jquery("a").each({ self: js.Dynamic =>
        val href = jquery(self).attr("href")
        if (!js.isUndefined(href))
          links += href.asInstanceOf[String]
        ()
      }: js.ThisFunction0[js.Dynamic, Unit])
    }

    links.result().iterator
  }

  def regexLookbehind: String = ":"

}
