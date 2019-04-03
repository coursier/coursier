package coursier.internal

import coursier.params.Mirror

abstract class PlatformMirrorConfFile {
  def mirrors(): Seq[Mirror] =
    Nil
}
