package coursier.core

import java.io.CharArrayReader
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

import coursier.util.{SaxHandler, Xml}
import org.xml.sax
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

import scala.collection.compat.immutable.LazyList
import scala.xml.{Attribute, Elem, MetaData, Null}

package object compatibility {

  implicit class RichChar(val c: Char) extends AnyVal {
    def letterOrDigit = c.isLetterOrDigit
    def letter = c.isLetter
  }

  private val utf8Bom = "\ufeff"

  private def entityIdx(s: String, fromIdx: Int = 0): Option[(Int, Int)] = {

    var i = fromIdx
    var found = Option.empty[(Int, Int)]
    while (found.isEmpty && i < s.length) {
      if (s.charAt(i) == '&') {
        val start = i
        i += 1
        var isAlpha = true
        while (isAlpha && i < s.length) {
          val c = s.charAt(i)
          if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z'))
            isAlpha = false
          else
            i += 1
        }
        if (start + 1 < i && i < s.length) {
          assert(!isAlpha)
          if (s.charAt(i) == ';') {
            i += 1
            found = Some((start, i))
          }
        }
      } else
        i += 1
    }

    found
  }

  private def substituteEntities(s: String): String = {

    val b = new StringBuilder
    lazy val a = s.toCharArray

    var i = 0

    var j = 0
    while (j < s.length && j < utf8Bom.length && s.charAt(i) == utf8Bom.charAt(j))
      j += 1

    if (j == utf8Bom.length)
      i = j

    var found = Option.empty[(Int, Int)]
    while ({
      found = entityIdx(s, i)
      found.nonEmpty
    }) {
      val from = found.get._1
      val to = found.get._2

      b.appendAll(a, i, from - i)

      val name = s.substring(from, to)
      val replacement = Entities.map.getOrElse(name, name)
      b.appendAll(replacement)

      i = to
    }

    if (i == 0)
      s
    else
      b.appendAll(a, i, s.length - i).result()
  }

  def xmlPreprocess(s: String): String =
    substituteEntities(s)

  private final class XmlHandler(handler: SaxHandler) extends DefaultHandler {
    override def startElement(uri: String, localName: String, qName: String, attributes: sax.Attributes): Unit =
      handler.startElement(qName)
    override def characters(ch: Array[Char], start: Int, length: Int): Unit =
      handler.characters(ch, start, length)
    override def endElement(uri: String, localName: String, qName: String): Unit =
      handler.endElement(qName)
  }

  private lazy val spf = {
    val spf0 = SAXParserFactory.newInstance()
    spf0.setNamespaceAware(false)
    spf0
  }

  def xmlParseSax(str: String, handler: SaxHandler): handler.type = {

    val str0 = xmlPreprocess(str)

    val saxParser = spf.newSAXParser()
    val xmlReader = saxParser.getXMLReader
    xmlReader.setContentHandler(new XmlHandler(handler))
    xmlReader.parse(new InputSource(new CharArrayReader(str0.toCharArray)))
    handler
  }

  def xmlParseDom(s: String): Either[String, Xml.Node] = {

    val content = xmlPreprocess(s)

    val parse =
      try Right(scala.xml.XML.loadString(content))
      catch { case e: Exception => Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")")) }

    parse.map(xmlFromElem)
  }

  def xmlFromElem(elem: Elem): Xml.Node = {

    def fromNode(node: scala.xml.Node): Xml.Node =
      new Xml.Node {
        lazy val attributes = {
          def helper(m: MetaData): LazyList[(String, String, String)] =
            m match {
              case Null => LazyList.empty
              case attr =>
                val pre = attr match {
                  case a: Attribute => Option(node.getNamespace(a.pre)).getOrElse("")
                  case _ => ""
                }

                val value = attr.value.collect {
                  case scala.xml.Text(t) => t
                }.mkString("")

                (pre, attr.key, value) #:: helper(m.next)
            }

          helper(node.attributes).toVector
        }
        def label = node.label
        def children = node.child.map(fromNode).toSeq
        def isText = node match { case _: scala.xml.Text => true; case _ => false }
        def textContent = node.text
        def isElement = node match { case _: scala.xml.Elem => true; case _ => false }

        override def toString = node.toString
      }

    fromNode(elem)
  }

  def xmlParse(s: String): Either[String, Xml.Node] = {

    val content = substituteEntities(s)

    val parse =
      try Right(scala.xml.XML.loadString(content))
      catch { case e: Exception => Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")")) }

    parse.map(xmlFromElem)
  }

  def encodeURIComponent(s: String): String =
    new java.net.URI(null, null, null, -1, s, null, null) .toASCIIString

  def regexLookbehind: String = "<="

  def coloredOutput: Boolean =
    // Same as coursier.paths.Util.useColorOutput()
    System.getenv("INSIDE_EMACS") == null &&
      Option(System.getenv("COURSIER_PROGRESS"))
        .map(_.toLowerCase(Locale.ROOT))
        .collect {
          case "true" | "1" | "enable" => true
          case "false" | "0" | "disable" => false
        }
        .getOrElse {
          System.getenv("COURSIER_NO_TERM") == null
        }

  @deprecated("Unused internally, likely to be removed in the future", "2.0.0-RC6-19")
  def hasConsole: Boolean =
    System.console() != null

}
