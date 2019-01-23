package coursier.benchmark

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import javax.xml.parsers.SAXParserFactory
import org.xml.sax
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

object Parse {

  private lazy val spf = {
    val spf0 = SAXParserFactory.newInstance()
    spf0.setNamespaceAware(true) // ???
    spf0
  }

  def parseRawPomSax(str: String): Unit = {

    val handler = new DefaultHandler {

      private[this] var path = List.empty[String]

      override def startElement(uri: String, localName: String, qName: String, attributes: sax.Attributes): Unit = {
        val p = path.headOption.fold(localName)(_ + ":" + localName)
        path = p :: path
      }
      override def characters(ch: Array[Char], start: Int, length: Int): Unit =
        ()
      override def endElement(uri: String, localName: String, qName: String): Unit = {
        path = path.tail
      }
    }

    val saxParser = spf.newSAXParser()
    val xmlReader = saxParser.getXMLReader
    xmlReader.setContentHandler(handler)
    xmlReader.parse(new InputSource(new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8))))
  }


}