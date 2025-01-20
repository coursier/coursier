package coursier.core

import java.util.regex.Pattern.quote

import coursier.core.compatibility._

object Parse {

  // matches revisions with a '+' appended, e.g. "1.2.+", "1.2+" or "1.2.3-+"
  private val latestSubRevision = "(.*[^.-])([.-]?)[+]".r

  private val multiVersionIntervalSplit =
    ("(?" + regexLookbehind + "[" + quote("])") + "]),(?=[" + quote("([") + "])").r

  val fallbackConfigRegex = {
    val noPar = "([^" + quote("()") + "]*)"
    "^" + noPar + quote("(") + noPar + quote(")") + "$"
  }.r

  // TODO Make that a method of Configuration?
  def withFallbackConfig(config: Configuration): Option[(Configuration, Configuration)] =
    Parse.fallbackConfigRegex.findAllMatchIn(config.value).toSeq match {
      case Seq(m) =>
        assert(m.groupCount == 2)
        val main     = Configuration(config.value.substring(m.start(1), m.end(1)))
        val fallback = Configuration(config.value.substring(m.start(2), m.end(2)))
        Some((main, fallback))
      case _ =>
        None
    }

}
