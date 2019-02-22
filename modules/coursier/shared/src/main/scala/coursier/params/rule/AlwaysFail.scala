package coursier.params.rule

import coursier.core.Resolution
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule

/**
  * Always fails.
  *
  * Mostly for testing.
  *
  * If `doTryResolve` is true, `tryResolve` will return the current Resolution, as if it was attempting to address the
  * issue. Else, it will fail early.
  */
final case class AlwaysFail(doTryResolve: Boolean = false) extends Rule {

  import AlwaysFail._

  type C = Nope

  def check(res: Resolution): Option[Nope] =
    Some(new Nope(this))
  def tryResolve(res: Resolution, conflict: Nope): Either[UnsatisfiableRule, Resolution] =
    if (doTryResolve)
      Right(res)
    else
      Left(new NopityNope(res, this, conflict))

}

object AlwaysFail {

  final class Nope(override val rule: AlwaysFail) extends UnsatisfiedRule(rule, "nope") {}
  final class NopityNope(resolution: Resolution, rule: AlwaysFail, conflict: Nope)
    extends UnsatisfiableRule(resolution, rule, conflict, "Nopity nope")

}
