package coursier.error.conflict

import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.params.rule.Rule

final class StrictRule(
  rule: Rule,
  conflict: UnsatisfiedRule
) extends UnsatisfiableRule(
  rule,
  conflict,
  s"Rule $rule not satisfied: $conflict"
)
