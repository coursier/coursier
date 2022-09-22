package coursier

import coursier.params.{Mirror, MirrorConfFile}

abstract class PlatformResolve {

  type Path = String

  def defaultConfFiles: Seq[Path] =
    Nil
  def defaultMirrorConfFiles: Seq[MirrorConfFile] =
    Nil

  def confFileMirrors(confFile: Path): Seq[Mirror] =
    Nil

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

  def proxySetup(): Unit =
    ()

}
