package coursier.core

import java.util.regex.Pattern.quote

import coursier.core.compatibility._
import coursier.version.{Version, VersionConstraint, VersionInterval}

object Parse {

  def version(s: String): Option[Version] = {
    val trimmed = s.trim
    if (trimmed.isEmpty || trimmed.exists(c => c != '.' && c != '-' && c != '_' && c != '+' && !c.letterOrDigit)) None
    else Some(Version(trimmed))
  }

  // matches revisions with a '+' appended, e.g. "1.2.+", "1.2+" or "1.2.3-+"
  private val latestSubRevision = "(.*[^.-])([.-]?)[+]".r

  def ivyLatestSubRevisionInterval(s: String): Option[VersionInterval] =
    s match {
      case latestSubRevision(prefix, delim) =>
        for {
          from <- version(prefix)
          if from.items.nonEmpty
          max = (if (delim.isEmpty) "." else delim) + "max"
          to <- version(prefix + max)
          // the contrary would mean something went wrong in the loose substitution above
          if from.items.init == to.items.dropRight(1).init
        } yield VersionInterval(Some(from), Some(to), fromIncluded = true, toIncluded = true)
      case _ =>
        None
    }

  val fallbackConfigRegex = {
    val noPar = "([^" + quote("()") + "]*)"
    "^" + noPar + quote("(") + noPar + quote(")") + "$"
  }.r

  // TODO Make that a method of Configuration?
  def withFallbackConfig(config: Configuration): Option[(Configuration, Configuration)] =
    Parse.fallbackConfigRegex.findAllMatchIn(config.value).toSeq match {
      case Seq(m) =>
        assert(m.groupCount == 2)
        val main = Configuration(config.value.substring(m.start(1), m.end(1)))
        val fallback = Configuration(config.value.substring(m.start(2), m.end(2)))
        Some((main, fallback))
      case _ =>
        None
    }

}
