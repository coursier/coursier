package coursier

import coursier.params.MirrorConfFile

abstract class PlatformResolve {

  protected def defaultMirrorConfFiles: Seq[MirrorConfFile] =
    Nil

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

}
