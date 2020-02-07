package coursier.launcher

sealed abstract class ClassPathEntry extends Product with Serializable

object ClassPathEntry {

  final case class Url(url: String) extends ClassPathEntry
  final case class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClassPathEntry

}
