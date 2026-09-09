package coursier.params
import dataclass.data

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class MirrorConfFile(
  path: String,
  optional: Boolean = true
) extends coursier.internal.PlatformMirrorConfFile
