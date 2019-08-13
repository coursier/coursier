package coursier.error.conflict

import coursier.params.rule.Rule

abstract class UnsatisfiedRule(
  val rule: Rule,
  message: String,
  cause: Throwable = null
) extends Exception(s"Unsatisfied rule ${rule.repr}: $message", cause)
