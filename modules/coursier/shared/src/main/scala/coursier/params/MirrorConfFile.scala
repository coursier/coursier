package coursier.params

final case class MirrorConfFile(
  path: String,
  optional: Boolean = true
) extends coursier.internal.PlatformMirrorConfFile
