package coursier.cache.server

import java.util.Locale

/** Decides which artifact URLs a cache server accepts to download
  *
  * A prefix ending with `*` matches any URL starting with what precedes the `*`. A prefix ending
  * with `/` matches any URL starting with it. Any other prefix matches the URL equal to it, and the
  * URLs under it, like `https://repo1.maven.org/maven2` matches
  * `https://repo1.maven.org/maven2/org/…` but not `https://repo1.maven.org/maven2-other/…`.
  *
  * Schemes and hosts are compared ignoring case. As soon as there's a rule to enforce, URLs with
  * `.` or `..` path segments are rejected, as these could escape an allowed prefix.
  *
  * @param defaultAllow
  *   whether URLs matching neither `allow` nor `disallow` are allowed
  * @param allow
  *   prefixes of allowed URLs, which win over `disallow` if `defaultAllow` is true
  * @param disallow
  *   prefixes of disallowed URLs, which win over `allow` if `defaultAllow` is false
  */
final class UrlFilter(
  val defaultAllow: Boolean,
  val allow: Seq[String],
  val disallow: Seq[String]
) {

  private val allow0    = allow.map(UrlFilter.normalize)
  private val disallow0 = disallow.map(UrlFilter.normalize)
  private val hasRules  = !defaultAllow || disallow.nonEmpty

  def isAllowed(url: String): Boolean =
    !(hasRules && UrlFilter.hasDotSegments(url)) && matchesRules(url)

  private def matchesRules(url: String): Boolean = {
    val url0                             = UrlFilter.normalize(url)
    def matches(prefix: String): Boolean =
      if (prefix.endsWith("*"))
        url0.startsWith(prefix.stripSuffix("*"))
      else if (prefix.endsWith("/"))
        url0.startsWith(prefix)
      else
        url0 == prefix || url0.startsWith(prefix + "/")
    if (defaultAllow)
      !disallow0.exists(matches) || allow0.exists(matches)
    else
      allow0.exists(matches) && !disallow0.exists(matches)
  }

  override def toString: String =
    s"UrlFilter(defaultAllow=$defaultAllow, allow=$allow, disallow=$disallow)"
}

object UrlFilter {

  /** Allows every URL */
  val allowAll: UrlFilter = new UrlFilter(defaultAllow = true, Nil, Nil)

  private def hasDotSegments(url: String): Boolean =
    url
      .replace("%2e", ".")
      .replace("%2E", ".")
      .split(Array('/', '\\', '?', '#'))
      .exists(elem => elem == "." || elem == "..")

  /** Lower-cases the scheme and the authority of `url`, leaving the rest as is */
  private def normalize(url: String): String = {
    val schemeEnd = url.indexOf(':')
    if (schemeEnd < 0) url
    else {
      val authorityEnd =
        if (url.startsWith("//", schemeEnd + 1)) {
          val idx = url.indexWhere(c => c == '/' || c == '?' || c == '#', schemeEnd + 3)
          if (idx < 0) url.length else idx
        }
        else
          schemeEnd + 1
      url.substring(0, authorityEnd).toLowerCase(Locale.ROOT) + url.substring(authorityEnd)
    }
  }
}
