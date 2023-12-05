package coursier

import coursier.core.Repository
import coursier.params.{Mirror, MirrorConfFile}

abstract class PlatformResolve {

  type Path = String

  def defaultConfFiles: Seq[Path] =
    Nil
  def defaultMirrorConfFiles: Seq[MirrorConfFile] =
    Nil

  def confFileMirrors(confFile: Path): Seq[Mirror] =
    Nil
  def confFileRepositories(confFile: Path): Option[Seq[Repository]] =
    None

  val defaultRepositories: Seq[Repository] =
    Seq(
      Repositories.central
    )

  def proxySetup(): Unit =
    ()

}
