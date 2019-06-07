package coursier.params.rule

import coursier.core.Resolution
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.Conflict
import coursier.util.ModuleMatcher

final case class Strict(
  include: Set[ModuleMatcher] = Set(ModuleMatcher.all),
  exclude: Set[ModuleMatcher] = Set.empty
) extends Rule {

  import Strict._

  type C = EvictedDependencies

  def check(res: Resolution): Option[EvictedDependencies] = {

    val conflicts = coursier.graph.Conflict(res).filter { c =>
      include.exists(_.matches(c.module)) &&
        !exclude.exists(_.matches(c.module))
    }

    if (conflicts.isEmpty)
      None
    else
      Some(new EvictedDependencies(this, conflicts))
  }

  def tryResolve(res: Resolution, conflict: EvictedDependencies): Either[UnsatisfiableRule, Resolution] =
    Left(new UnsatisfiableRule(res, this, conflict))
}

object Strict {

  final class EvictedDependencies(
    override val rule: Strict,
    val evicted: Seq[Conflict]
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
