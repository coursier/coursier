package coursier

import coursier.params.MirrorConfFile

abstract class PlatformResolve {

  def defaultMirrorConfFiles: Seq[MirrorConfFile] =
    Nil

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

}
