package coursier.util

import java.util.regex.Pattern

import coursier.core.{Module, ModuleName, Organization}

import scala.annotation.tailrec
import scala.util.matching.Regex

final case class ModuleMatcher(matcher: Module) {

  import ModuleMatcher.blobToPattern

  lazy val orgPattern = blobToPattern(matcher.organization.value)
  lazy val namePattern = blobToPattern(matcher.name.value)
  lazy val attributesPattern = matcher
    .attributes
    .mapValues(blobToPattern(_))
    .iterator
    .toMap

  def matches(module: Module): Boolean =
    orgPattern.pattern.matcher(module.organization.value).matches() &&
      namePattern.pattern.matcher(module.name.value).matches() &&
      module.attributes.keySet == attributesPattern.keySet &&
      attributesPattern.forall {
        case (k, p) =>
          module.attributes.get(k).exists(p.pattern.matcher(_).matches())
      }

}

object ModuleMatcher {

  def apply(org: Organization, name: ModuleName, attributes: Map[String, String] = Map.empty): ModuleMatcher =
    ModuleMatcher(Module(org, name, attributes))

  @tailrec
  private def blobToPattern(s: String, b: StringBuilder = new StringBuilder): Regex =
    if (s.isEmpty)
      b.result().r
    else {
      val idx = s.indexOf('*')
      if (idx < 0) {
        b ++= Pattern.quote(s)
        b.result().r
      } else {
        if (idx > 0)
          b ++= Pattern.quote(s.substring(0, idx))
        b ++= ".*"
        blobToPattern(s.substring(idx + 1), b)
      }
    }

}
