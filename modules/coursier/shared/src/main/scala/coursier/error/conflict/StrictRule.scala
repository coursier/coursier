package coursier.error.conflict

import coursier.core.Resolution
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.params.rule.Rule

final class StrictRule(
  resolution: Resolution,
  rule: Rule,
  conflict: UnsatisfiedRule
) extends UnsatisfiableRule(
  resolution,
  rule,
  conflict,
  s"Rule $rule not satisfied: $conflict"
)
