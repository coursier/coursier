package coursier.launcher

import dataclass.data

sealed abstract class ClassPathEntry extends Product with Serializable

object ClassPathEntry {

  @data class Url(url: String) extends ClassPathEntry
  @data class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClassPathEntry

}
