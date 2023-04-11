package coursier.cli.util

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.collection.JavaConverters._

object ClassPathUtil {

  def classPath(input: String): Seq[Path] =
    input.split(File.pathSeparator).filter(_.nonEmpty).flatMap { elem =>
      def allJarsOf(dir: Path): Seq[Path] =
        Files.list(dir)
          .iterator
          .asScala
          .filter { path =>
            val name = path.toString
            name.length >= ".jar".length &&
            name.substring(name.length() - ".jar".length).equalsIgnoreCase(".jar")
          }
          .toVector
      if (elem.endsWith("/*")) {
        val dir = Paths.get(elem.stripSuffix("/*"))
        allJarsOf(dir)
      }
      else if (elem.endsWith("/*.jar")) {
        val dir = Paths.get(elem.stripSuffix("/*.jar"))
        allJarsOf(dir)
      }
      else
        Seq(Paths.get(elem))
    }

}
