package coursier.params
import dataclass.data

@data class MirrorConfFile(
  path: String,
  optional: Boolean = true
) extends coursier.internal.PlatformMirrorConfFile
