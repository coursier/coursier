package coursier.core

import java.io.{CharArrayReader, StringWriter}
import java.util.Locale
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import javax.xml.XMLConstants
import coursier.util.{SaxHandler, Xml}
import org.xml.sax
import org.xml.sax.{InputSource, XMLReader}
import org.xml.sax.helpers.DefaultHandler

import java.lang.ThreadLocal
import scala.collection.compat.immutable.LazyList
import scala.util.control.NonFatal
import scala.xml.{Attribute, Elem, MetaData, Null}

package object compatibility {

  implicit class RichChar(val c: Char) extends AnyVal {
    def letterOrDigit = c.isLetterOrDigit
    def letter        = c.isLetter
  }

  private val utf8Bom              = "\ufeff"
  private val utf8BomLength        = utf8Bom.length
  private lazy val throwExceptions = java.lang.Boolean.getBoolean("coursier.core.throw-exceptions")

  private def entityIdx(s: String, fromIdx: Int): (Int, Int) = {
    val len = s.length
    var i   = s.indexOf('&', fromIdx)

    while (i >= 0 && i <= len - 3) { // Need at least &X; (3 chars)
      var j = i + 1
      while (
        j < len && {
          val c = s.charAt(j)
          (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
        }
      ) j += 1

      if (j > i + 1 && j < len && s.charAt(j) == ';')
        return (i, j + 1)

      i = s.indexOf('&', i + 1)
    }
    null
  }
  import java.io.{CharArrayReader, Reader, StringReader}

  object EntitySubstitution {

    def processToString(s: String): String = {
      val reader = process(s)
      try {
        val sb  = new StringBuilder(s.length);
        val buf = new Array[Char](8192);
        var n   = 0
        while ({ n = reader.read(buf); n != -1 })
          sb.appendAll(buf, 0, n)
        sb.toString
      }
      finally
        reader.close()
    }
    def process(s: String): Reader = {
      val len = s.length
      if (len == 0) return new StringReader("")

      // Skip BOM if present
      val hasBom = s.charAt(0) == '\uFEFF'
      val start  = if (hasBom) 1 else 0

      var dst: Array[Char] = null
      var readPtr          = start
      var writePtr         = 0

      // Leapfrog from ampersand to ampersand
      var nextAmp = s.indexOf('&', readPtr)

      if (nextAmp == -1) {
        // Fast path: No ampersands at all
        if (hasBom) {
          val reader = new StringReader(s)
          reader.skip(1)
          reader.mark(len - 1)
          return reader
        }
        return new StringReader(s)
      }

      while (readPtr < len)
        if (nextAmp == -1 || readPtr < nextAmp) {
          // We are between the current readPtr and the next ampersand (or end of string)
          val limit    = if (nextAmp == -1) len else nextAmp
          val chunkLen = limit - readPtr

          if (dst != null) {
            s.getChars(readPtr, limit, dst, writePtr)
            writePtr += chunkLen
          }
          else
            // If we haven't allocated dst yet, we just "virtually" move the writePtr
            writePtr += chunkLen
          readPtr = limit
        }
        else {
          // We are exactly at an ampersand
          val semiIdx = s.indexOf(';', readPtr + 1)

          // Spec check: entity must close within 11 chars (&...;)
          if (semiIdx != -1 && (semiIdx - readPtr) <= 11) {
            val entityNameWithAmpAndSemi = s.substring(readPtr, semiIdx + 1)
            val replacement              = Entities.mapFast(entityNameWithAmpAndSemi)

            if (replacement != null) {
              // Lazy initialization on first confirmed substitution
              if (dst == null) {
                dst = new Array[Char](len)
                // Copy everything skipped so far (the clean prefix, after BOM)
                s.getChars(start, readPtr, dst, 0)
                // writePtr was already tracking our virtual position
              }

              replacement.getChars(0, replacement.length, dst, writePtr)
              writePtr += replacement.length
              readPtr = semiIdx + 1
            }
            else {
              // Found &...; but not a spec entity, treat '&' as literal
              if (dst != null) dst(writePtr) = '&'
              writePtr += 1
              readPtr += 1
            }
          }
          else {
            // Lone '&' or ';' too far away
            if (dst != null) dst(writePtr) = '&'
            writePtr += 1
            readPtr += 1
          }

          // Find the next ampersand for the next iteration
          nextAmp = s.indexOf('&', readPtr)
        }

      if (dst == null) new StringReader(s)
      else new CharArrayReader(dst, 0, writePtr)
    }
  }

  def xmlPreprocess(s: String): String =
    EntitySubstitution.processToString(s)

  private final class XmlHandler(handler: SaxHandler) extends DefaultHandler {
    override def startElement(
      uri: String,
      localName: String,
      qName: String,
      attributes: sax.Attributes
    ): Unit =
      handler.startElement(qName)
    override def characters(ch: Array[Char], start: Int, length: Int): Unit =
      handler.characters(ch, start, length)
    override def endElement(uri: String, localName: String, qName: String): Unit =
      handler.endElement(qName)
  }

  private lazy val spf = {
    val spf0 = SAXParserFactory.newInstance()
    spf0.setNamespaceAware(false)

    // Fixing CVE-2022-46751: External Entity Reference Vulnerability
    def trySetFeature(feature: String, value: Boolean): Unit = {
      try spf0.setFeature(feature, value)
      catch {
        case _: ParserConfigurationException | _: sax.SAXNotRecognizedException | _: sax.SAXNotSupportedException =>
          ()
      }
    }
    // Allow doctype processing
    trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
    // Process XML in accordance with the XML specification to avoid conditions such as denial of service attacks
    trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    // Disallow external entities
    trySetFeature("http://xml.org/sax/features/external-general-entities", false)
    trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
    // Allow external dtd
    trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true)
    // Disallow XInclude processing
    try spf0.setXIncludeAware(false)
    catch {
      case e: UnsupportedOperationException => ()
    }

    spf0
  }

  private def newXmlReader = {
    val saxParser = spf.newSAXParser()
    saxParser.getXMLReader
  }
  private val reusedParser = ThreadLocal.withInitial(() => newXmlReader)
  def xmlParseSax(str: String, handler: SaxHandler): handler.type = {
    val reader = EntitySubstitution.process(str)
    try {
      val xmlReader = reusedParser.get()
      xmlReader.setContentHandler(new XmlHandler(handler))
      xmlReader.parse(new org.xml.sax.InputSource(reader))
      handler
    }
    catch {
      case NonFatal(e) =>
        reusedParser.remove()
        throw e
    }
  }

  def xmlParseDom(s: String): Either[String, Xml.Node] = {

    val content = xmlPreprocess(s)

    val parse =
      try Right(scala.xml.XML.loadString(content))
      catch {
        case e: Exception if !throwExceptions =>
          Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")"))
      }

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
                  case _            => ""
                }

                val value = attr.value.collect {
                  case scala.xml.Text(t) => t
                }.mkString("")

                (pre, attr.key, value) #:: helper(m.next)
            }

          helper(node.attributes).toVector
        }
        def label       = node.label
        def children    = node.child.map(fromNode).toSeq
        def isText      = node match { case _: scala.xml.Text => true; case _ => false }
        def textContent = node.text
        def isElement   = node match { case _: scala.xml.Elem => true; case _ => false }

        override def toString = node.toString
      }

    fromNode(elem)
  }

  def xmlParse(s: String): Either[String, Xml.Node] = {

    val content = EntitySubstitution.processToString(s)

    val parse =
      try Right(scala.xml.XML.loadString(content))
      catch {
        case e: Exception if !throwExceptions =>
          Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")"))
      }

    parse.map(xmlFromElem)
  }

  def encodeURIComponent(s: String): String =
    new java.net.URI(null, null, null, -1, s, null, null).toASCIIString

  def regexLookbehind: String = "<="

  def coloredOutput: Boolean =
    // Same as coursier.paths.Util.useColorOutput()
    System.getenv("INSIDE_EMACS") == null &&
    Option(System.getenv("COURSIER_PROGRESS"))
      .map(_.toLowerCase(Locale.ROOT))
      .collect {
        case "true" | "1" | "enable"   => true
        case "false" | "0" | "disable" => false
      }
      .getOrElse {
        System.getenv("COURSIER_NO_TERM") == null
      }

  @deprecated(
    "Unused internally, returns incorrect result on JDK >= 22, likely to be removed in the future",
    "2.0.0-RC6-19"
  )
  def hasConsole: Boolean =
    System.console() != null

}
