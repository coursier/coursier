package coursier.launcher

import dataclass.data

sealed abstract class ClassPathEntry extends Product with Serializable

object ClassPathEntry {

  @data(
    deprecatedSetters = true,
    deprecatedSettersMessage = "Use copy instead",
    deprecatedSettersSince = "2.1.25"
  ) case class Url(url: String) extends ClassPathEntry
  @data(
    deprecatedSetters = true,
    deprecatedSettersMessage = "Use copy instead",
    deprecatedSettersSince = "2.1.25"
  ) case class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClassPathEntry

}
