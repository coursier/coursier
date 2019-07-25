package coursier.util

private[coursier] object WebPage {

  def listElements(url: String, page: String): Iterator[String] =
    coursier.core.compatibility.listWebPageRawElements(page)
      .collect {
        case elem if elem.nonEmpty =>
          elem
            .stripPrefix(url)
            .stripPrefix(":") // bintray typically prepends these
      }
      .filter { n =>
        lazy val slashIdx = n.indexOf('/')
        n != "./" && n != "../" && n != "." && n != ".." && n != "/" && (slashIdx < 0 || slashIdx == n.length - 1)
      }

  def listDirectories(url: String, page: String): Iterator[String] =
    listElements(url, page)
      .filter(_.endsWith("/"))
      .map(s => s.substring(0, s.length - 1))

  def listFiles(url: String, page: String): Iterator[String] =
    listElements(url, page)
      .filter(!_.endsWith("/"))

}