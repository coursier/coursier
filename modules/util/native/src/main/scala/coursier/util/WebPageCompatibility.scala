package coursier.util

import java.util.regex.Pattern

private[coursier] abstract class WebPageCompatibility {

  /** Scala Native has no HTML parser to lean on (jsoup is JVM-only, cheerio is a Node module), so
    * `href` attributes of anchors are picked up with a regular expression.
    *
    * Directory listings of the repositories coursier talks to are simple enough for this - one
    * anchor per entry, no scripts, no attributes holding stray `<a` sequences.
    */
  def listWebPageRawElements(page: String): Iterator[String] = {
    val matcher = WebPageCompatibility.anchorHref.matcher(page)
    Iterator.continually(())
      .takeWhile(_ => matcher.find())
      .map { _ =>
        val quoted   = Option(matcher.group(2)).orElse(Option(matcher.group(3)))
        val attrHtml = quoted.orElse(Option(matcher.group(4))).getOrElse("")
        WebPageCompatibility.unescape(attrHtml)
      }
  }

}

private[coursier] object WebPageCompatibility {

  // <a … href="…" …> - the attribute value can be double-quoted, single-quoted, or unquoted
  private val anchorHref = Pattern.compile(
    "<a\\s[^>]*?href\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  )

  private def unescape(s: String): String =
    if (s.indexOf('&') < 0) s
    else
      s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

}
