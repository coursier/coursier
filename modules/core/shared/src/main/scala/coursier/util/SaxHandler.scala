package coursier.util

abstract class SaxHandler {
  def startElement(tagName: String): Unit // TODO attributes
  def characters(ch: Array[Char], start: Int, length: Int): Unit
  def endElement(tagName: String): Unit
}
