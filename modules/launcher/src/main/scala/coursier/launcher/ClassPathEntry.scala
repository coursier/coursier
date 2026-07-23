package coursier.launcher

import dataclass.data



sealed abstract class ClassPathEntry extends Product with Serializable

object ClassPathEntry {

  @data case class Url(url: String) extends ClassPathEntry
  @data case class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClassPathEntry

}
