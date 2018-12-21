package coursier.bootstrap

sealed abstract class ClasspathEntry extends Product with Serializable

object ClasspathEntry {

  final case class Url(url: String) extends ClasspathEntry
  final case class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClasspathEntry

}
