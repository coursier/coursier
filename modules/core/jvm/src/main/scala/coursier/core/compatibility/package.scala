package coursier.core

import java.io.CharArrayReader

import coursier.util.{SaxHandler, Xml}
import java.util.regex.Pattern.quote

import javax.xml.parsers.SAXParserFactory

import scala.collection.JavaConverters._
import scala.xml.{ Attribute, MetaData, Null }
import org.jsoup.Jsoup
import org.xml.sax
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

package object compatibility {

  implicit class RichChar(val c: Char) extends AnyVal {
    def letterOrDigit = c.isLetterOrDigit
    def letter = c.isLetter
  }

  private val entityPattern = (quote("&") + "[a-zA-Z]+" + quote(";")).r

  private val utf8Bom = "\ufeff"

  def xmlPreprocess(s: String): String = {

    val content =
      if (entityPattern.findFirstIn(s).isEmpty)
        s
      else
        Entities.entities.foldLeft(s) {
          case (s0, (target, replacement)) =>
            s0.replace(target, replacement)
        }

    content.stripPrefix(utf8Bom)
  }

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

    def parse =
      try Right(scala.xml.XML.loadString(content))
      catch { case e: Exception => Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")")) }

    def fromNode(node: scala.xml.Node): Xml.Node =
      new Xml.Node {
        lazy val attributes = {
          def helper(m: MetaData): Stream[(String, String, String)] =
            m match {
              case Null => Stream.empty
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

    parse.right
      .map(fromNode)
  }

  def encodeURIComponent(s: String): String =
    new java.net.URI(null, null, null, -1, s, null, null) .toASCIIString

  def listWebPageRawElements(page: String): Seq[String] =
    Jsoup.parse(page)
      .select("a")
      .asScala
      .toVector
      .map(_.attr("href"))

  def regexLookbehind: String = "<="

}
