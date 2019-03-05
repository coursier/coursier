package coursier.params.rule

import coursier.core.Resolution
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.Conflict

case object Strict extends Rule {

  type C = EvictedDependencies

  def check(res: Resolution): Option[EvictedDependencies] = {

    val conflicts = coursier.graph.Conflict(res)

    if (conflicts.isEmpty)
      None
    else
      Some(new EvictedDependencies(this, conflicts))
  }

  def tryResolve(res: Resolution, conflict: EvictedDependencies): Either[UnsatisfiableRule, Resolution] =
    Left(new UnsatisfiableRule(res, this, conflict))

  final class EvictedDependencies(
    override val rule: Strict.type,
    val evicted: Seq[Conflict]
  ) extends UnsatisfiedRule(
    rule,
    s"Found evicted dependencies:\n" +
      evicted.map(_.toString + "\n").mkString
  )

  final class UnsatisfiableRule(
    resolution: Resolution,
    override val rule: Strict.type,
    override val conflict: EvictedDependencies
  ) extends coursier.error.ResolutionError.UnsatisfiableRule(
    resolution,
    rule,
    conflict,
    conflict.getMessage
  )

}
