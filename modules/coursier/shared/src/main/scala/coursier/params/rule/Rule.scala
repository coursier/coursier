package coursier.params.rule

import coursier.core.Resolution
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.{StrictRule, UnsatisfiedRule}

abstract class Rule extends Product with Serializable {

  type C <: UnsatisfiedRule

  def check(res: Resolution): Option[C]
  def tryResolve(res: Resolution, conflict: C): Either[UnsatisfiableRule, Resolution]

  def enforce(res: Resolution, ruleRes: RuleResolution): Either[UnsatisfiableRule, Either[UnsatisfiedRule, Option[Resolution]]] =
    check(res) match {
      case None =>
        Right(Right(None))
      case Some(c) =>
        ruleRes match {
          case RuleResolution.Fail =>
            Left(new StrictRule(res, this, c))
          case RuleResolution.Warn =>
            Right(Left(c))
          case RuleResolution.TryResolve =>
            tryResolve(res, c)
              .right.map(r => Right(Some(r)))
        }
    }
}
