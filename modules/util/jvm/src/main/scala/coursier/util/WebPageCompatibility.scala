package coursier.util

import org.jsoup.Jsoup

import scala.jdk.CollectionConverters._

private[coursier] abstract class WebPageCompatibility {

  def listWebPageRawElements(page: String): Iterator[String] =
    Jsoup.parse(page)
      .select("a")
      .asScala
      .iterator
      .map(_.attr("href"))

}
