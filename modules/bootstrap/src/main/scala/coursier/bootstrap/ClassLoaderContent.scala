package coursier.bootstrap

import scala.collection.mutable

final case class ClassLoaderContent(
  entries: Seq[ClasspathEntry],
  loaderName: String = ""
)

object ClassLoaderContent {

  def withUniqueFileNames(content: Seq[ClassLoaderContent]): Seq[ClassLoaderContent] = {

    val seen = new mutable.HashMap[String, Int]

    content.map { c =>
      c.copy(
        entries = c.entries.map {
          case r: ClasspathEntry.Resource =>
            val n = seen.getOrElse(r.fileName, 0)
            seen(r.fileName) = n + 1
            if (n == 0)
              r
            else {
              val extIdx = r.fileName.lastIndexOf('.')
              val fileName0 =
                if (extIdx < 0)
                  s"${r.fileName}-$n"
                else
                  s"${r.fileName.take(extIdx)}-$n.${r.fileName.drop(extIdx + 1)}"

              r.copy(fileName = fileName0)
            }
          case e =>
            e
        }
      )
    }
  }

}
