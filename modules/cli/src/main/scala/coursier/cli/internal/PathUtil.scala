package coursier.cli.internal

import java.io.File
import java.nio.file.{Path, Paths}

object PathUtil {

  def isInPath(p: Path): Boolean = {
    val p0          = p.toAbsolutePath.normalize
    val pathValue   = Option(System.getenv("PATH")).getOrElse("")
    val pathEntries = pathValue.split(File.pathSeparator).filter(_.nonEmpty)
    def pathDirs    = pathEntries.iterator.map(Paths.get(_).toAbsolutePath.normalize)
    pathDirs.exists { pathDir =>
      p0.getNameCount == pathDir.getNameCount + 1 &&
      p0.startsWith(pathDir)
    }
  }

}
