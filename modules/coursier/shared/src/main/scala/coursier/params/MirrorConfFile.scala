package coursier.params

import dataclass.data

@data case class MirrorConfFile(
  path: String,
  optional: Boolean = true
) extends coursier.internal.PlatformMirrorConfFile
