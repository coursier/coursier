package coursier.core

import java.util.concurrent.ConcurrentHashMap

import coursier.util.Xml
import java.util.regex.Pattern.quote

import scala.collection.JavaConverters._
import scala.xml.{Attribute, MetaData, Null}
import org.jsoup.Jsoup

package object compatibility {

  implicit class RichChar(val c: Char) extends AnyVal {
    def letterOrDigit = c.isLetterOrDigit
    def letter = c.isLetter
  }

  private val entityPattern = (quote("&") + "[a-zA-Z]+" + quote(";")).r

  private val utf8Bom = "\ufeff"

  private val xmlCache = new ConcurrentHashMap[String, Either[String, Xml.Node]]

  def xmlParse(s: String): Either[String, Xml.Node] = {
    // val start0 = System.nanoTime()
    Option(xmlCache.get(s)) match {
      case None =>
        // val start = System.nanoTime()
        val res = xmlParse0(s)
        // val end = System.nanoTime()
        // System.err.println(s"Spent ${(end - start).toDouble / 1000000L} ms parsing ${s.length} bytes of XML") // not exactly bytes, but w/e…
        Option(xmlCache.putIfAbsent(s, res))
          .getOrElse(res)
      case Some(e) =>
        // val end = System.nanoTime()
        // System.err.println(s"Spent ${(end - start0).toDouble / 1000000L} ms looking up ${s.length} bytes of XML") // not exactly bytes, but w/e…
        e
    }
  }

  def xmlParse0(s: String): Either[String, Xml.Node] = {

    val content =
      if (entityPattern.findFirstIn(s).isEmpty)
        s
      else
        Entities.entities.foldLeft(s) {
          case (s0, (target, replacement)) =>
            s0.replace(target, replacement)
        }

    def parse =
      try Right(scala.xml.XML.loadString(content.stripPrefix(utf8Bom)))
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
