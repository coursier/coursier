package coursier.params.rule

import coursier.core.Resolution
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.Conflict.Conflicted
import coursier.util.ModuleMatcher

final case class Strict(
  include: Set[ModuleMatcher] = Set(ModuleMatcher.all),
  exclude: Set[ModuleMatcher] = Set.empty,
  includeByDefault: Boolean = false,
  ignoreIfForcedVersion: Boolean = true,
  semVer: Boolean = false
) extends Rule {

  import Strict._

  type C = EvictedDependencies

  def check(res: Resolution): Option[EvictedDependencies] = {

    val conflicts = coursier.graph.Conflict.conflicted(res, semVer = semVer).filter { c =>
      val conflict = c.conflict
      val ignore = ignoreIfForcedVersion && res.forceVersions.get(conflict.module).contains(conflict.version)
      def matches =
        if (includeByDefault)
          include.exists(_.matches(conflict.module)) ||
            !exclude.exists(_.matches(conflict.module))
        else
          include.exists(_.matches(conflict.module)) &&
            !exclude.exists(_.matches(conflict.module))
      !ignore && matches
    }

    if (conflicts.isEmpty)
      None
    else
      Some(new EvictedDependencies(this, conflicts))
  }

  def tryResolve(res: Resolution, conflict: EvictedDependencies): Either[UnsatisfiableRule, Resolution] =
    Left(new UnsatisfiableRule(res, this, conflict))

  override def repr: String = {
    val b = new StringBuilder("Strict(")
    var anyElem = false
    if (include.nonEmpty) {
      anyElem = true
      b ++= include.toVector.map(_.matcher.repr).sorted.mkString(" | ")
    }
    if (exclude.nonEmpty) {
      if (anyElem)
        b ++= ", "
      else
        anyElem = true
      b ++= "exclude="
      b ++= exclude.toVector.map(_.matcher.repr).sorted.mkString(" | ")
    }
    if (includeByDefault) {
      if (anyElem)
        b ++= ", "
      else
        anyElem = true
      b ++= "ignoreIfForcedVersion=true"
    }
    if (!ignoreIfForcedVersion) {
      if (anyElem)
        b ++= ", "
      else
        anyElem = true
      b ++= "ignoreIfForcedVersion=false"
    }
    b += ')'
    b.result()
  }
}

object Strict {

  final class EvictedDependencies(
    override val rule: Strict,
    val evicted: Seq[Conflicted]
  ) extends UnsatisfiedRule(
    rule,
    s"Found evicted dependencies:\n" +
      evicted.map(_.repr + "\n").mkString
  )

  final class UnsatisfiableRule(
    resolution: Resolution,
    override val rule: Strict,
    override val conflict: EvictedDependencies
  ) extends coursier.error.ResolutionError.UnsatisfiableRule(
    resolution,
    rule,
    conflict,
    conflict.getMessage
  )

}
