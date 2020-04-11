package coursier.cli.install

import java.nio.file.{Path, Paths}

final case class ListParams(
    installPath: Path
)

object ListParams {
  def apply(options: ListOptions): ListParams = {
    val dir = options.installDir.filter(_.nonEmpty) match {
      case Some(d) => Paths.get(d)
      case None    => SharedInstallParams.defaultDir
    }
    ListParams(dir)
  }
}
