package coursier.core

import coursier.core.PropertyExpr.Substitution

import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

private[coursier] sealed abstract class PropertyExpr {
  def hasProperties: Boolean = this match {
    case PropertyLiteral(_)   => false
    case PropertyReference(_) => true
    case Composite(_)         => true
  }
  final def substitute(s: String, lookup: PropertyValueLookup, trim: Boolean): String =
    try
      this match {
        case PropertyLiteral(value) =>
          value
        case PropertyReference(name) =>
          val resolvedExpr = lookup.lookupOrNull(name)
          if (resolvedExpr == null) s
          else {
            val result = resolvedExpr match {
              case PropertyLiteral(v) => v
              case spe: SimplePropertyExpr =>
                PropertyExpr.resolvePartToString(spe, lookup, trim, Nil, 1)
              case Composite(ps) => PropertyExpr.renderSubstitutions(
                  s,
                  ps,
                  lookup,
                  trim,
                  Nil,
                  forceSubstitute = true,
                  1
                )
            }
            if (trim) result.trim else result
          }
        case Composite(parts) =>
          PropertyExpr.renderSubstitutions(s, parts, lookup, trim)
      }
    catch {
      case _: PropertyExpr.CyclicPropertyException =>
        s
    }
  final def applySubstitution(value: String, f: (String => String)): String = f match {
    case s: Substitution =>
      s.apply(value)
//      s.applyWithPropertyExpr(value, this)
    case _ => f(value)
  }

}

private sealed abstract class SimplePropertyExpr extends PropertyExpr

private final case class PropertyLiteral(value: String) extends SimplePropertyExpr

private final case class PropertyReference(name: String) extends SimplePropertyExpr

private final case class Composite(parts: Array[SimplePropertyExpr]) extends PropertyExpr

private[coursier] object PropertyExpr {
  private val InterpolationStartToken = "${"
  private val InterpolationEndToken   = "}"

  def applySubstitution(value: String, f: (String => String)): String = f match {
    case s: Substitution =>
      s.apply(value)
    case _ => f(value)
  }

  def parse(s: String): PropertyExpr =
    if (s.indexOf(InterpolationStartToken) < 0)
      PropertyLiteral(s)
    else parseSubstituteProps(s)

  final class Substitution(lookup: PropertyValueLookup, trim: Boolean) extends (String => String) {
    override def apply(v1: String): String =
      PropertyExpr.parse(v1).substitute(v1, lookup, trim)
  }

  private val CycleTrackingDepthStart = 8
  private def resolvePartToString(
    part: SimplePropertyExpr,
    lookup: PropertyValueLookup,
    trim: Boolean,
    seen: List[String],
    depth: Int
  ): String = part match {
    case PropertyLiteral(value)  => value
    case PropertyReference(name) =>
      // Skip cycle tracking below depth CycleTrackingDepthStart: no allocation, no list scan
      val isCycle = depth >= CycleTrackingDepthStart && seen.contains(name)
      val resolvedExpr =
        if (isCycle) throw new CyclicPropertyException else lookup.lookupOrNull(name)
      if (resolvedExpr == null)
        s"$${$name}"
      else {
        val nextSeen = if (depth < CycleTrackingDepthStart) Nil else name :: seen
        val result = resolvedExpr match {
          case PropertyLiteral(v) => v
          case spe: SimplePropertyExpr =>
            resolvePartToString(spe, lookup, trim, nextSeen, depth + 1)
          case Composite(ps) =>
            renderSubstitutions("", ps, lookup, trim, nextSeen, forceSubstitute = true, depth + 1)
        }
        if (trim) result.trim else result
      }
  }
  private class CyclicPropertyException
      extends RuntimeException("Cyclic property reference detected")

  private def renderSubstitutions(
    raw: String,
    parts: Array[SimplePropertyExpr],
    lookup: PropertyValueLookup,
    trim: Boolean,
    seen: List[String] = Nil,
    forceSubstitute: Boolean = false,
    depth: Int = 0
  ): String = {
    val n = parts.length

    // Probe: before allocating anything, verify at least one ref resolves.
    // Only runs at top level (!forceSubstitute), where seen=Nil, so no cycle check needed.
    if (!forceSubstitute) {
      var hasSubst = false
      var j        = 0
      while (j < n && !hasSubst) {
        parts(j) match {
          case PropertyReference(name) =>
            if (lookup.lookupOrNull(name) != null) hasSubst = true
          case _ =>
        }
        j += 1
      }
      if (!hasSubst) return raw
    }

    // Single-part fast path: no StringBuilder needed
    if (n == 1)
      return resolvePartToString(parts(0), lookup, trim, seen, depth)

    // Single forward pass: capacity heuristic avoids resizing in the common case
    val sb = new java.lang.StringBuilder(if (raw.nonEmpty) raw.length else 16)
    var i  = 0
    while (i < n) {
      sb.append(resolvePartToString(parts(i), lookup, trim, seen, depth))
      i += 1
    }
    sb.toString
  }

  private def parseSubstituteProps(s: String): PropertyExpr = {
    // Fast path: Check if any interpolation exists at all
    val firstTokenIdx = s.indexOf(InterpolationStartToken)

    if (firstTokenIdx == -1)
      // Return early without any allocations if no tokens are found
      PropertyLiteral(s)
    else {
      // Slow path: Interpolation exists, proceed with parsing
      val builder = Array.newBuilder[SimplePropertyExpr]

      var idx = 0

      val startLen = InterpolationStartToken.length
      val endLen   = InterpolationEndToken.length

      while (idx < s.length) {
        val startIdx = if (idx == 0) firstTokenIdx else s.indexOf(InterpolationStartToken, idx)

        if (startIdx == -1) {
          appendLiteralToArray(builder, s.substring(idx))
          idx = s.length
        }
        else {
          if (startIdx > idx)
            appendLiteralToArray(builder, s.substring(idx, startIdx))

          val endIdx = s.indexOf(InterpolationEndToken, startIdx + startLen)

          if (endIdx != -1) {
            builder += PropertyReference(s.substring(startIdx + startLen, endIdx))
            idx = endIdx + endLen
          }
          else {
            // Unclosed token: append first char and move on
            appendLiteralToArray(builder, InterpolationStartToken.substring(0, 1))
            idx = startIdx + 1
          }
        }
      }

      val resultParts = builder.result()

      // Final check: If parsing resulted in only one part, don't wrap it in Composite
      resultParts.length match {
        case 0 => PropertyLiteral(s)
        case 1 => resultParts(0)
        case _ => Composite(resultParts)
      }
    }
  }

  private def appendLiteralToArray(
    builder: mutable.ArrayBuilder[SimplePropertyExpr],
    value: String
  ): Unit = {
    if (value.nonEmpty)
      builder += PropertyLiteral(value)
  }
}

private[coursier] trait PropertyValueLookup {
  def lookup(key: String): Option[PropertyExpr] = Option(lookupOrNull(key))
  def lookupOrNull(key: String): PropertyExpr
}
