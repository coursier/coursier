package coursier.cli.util

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.regex.{Matcher, Pattern}

import scala.collection.JavaConverters._

object ClassPathUtil {

  private val propertyRegex = Pattern.compile(
    Pattern.quote("${") + "[^" + Pattern.quote("{[()]}") + "]*" + Pattern.quote("}")
  )

  def classPath(input: String, getProperty: String => Option[String]): Seq[Path] =
    input.split(File.pathSeparator).filter(_.nonEmpty).flatMap { elem =>
      val processedElem = {
        var value            = elem
        var matcher: Matcher = null

        while ({
          matcher = propertyRegex.matcher(value)
          matcher.find()
        }) {
          val start    = matcher.start(0)
          val end      = matcher.end(0)
          val subKey   = value.substring(start + 2, end - 1)
          val subValue = getProperty(subKey).getOrElse("")
          value = value.substring(0, start) + subValue + value.substring(end)
        }

        value
      }
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
      if (processedElem.endsWith("/*")) {
        val dir = Paths.get(processedElem.stripSuffix("/*"))
        allJarsOf(dir)
      }
      else if (processedElem.endsWith("/*.jar")) {
        val dir = Paths.get(processedElem.stripSuffix("/*.jar"))
        allJarsOf(dir)
      }
      else
        Seq(Paths.get(processedElem))
    }

}
