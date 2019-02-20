package coursier.error

import coursier.core.{Dependency, Module}
import coursier.error.conflict.UnsatisfiedRule
import coursier.params.rule.Rule
import coursier.util.Print

sealed abstract class ResolutionError(message: String, cause: Throwable = null) extends CoursierError(message, cause)

object ResolutionError {

  final class MaximumIterationReached extends Simple(
    "Maximum number of iterations reached"
  )

  final class CantDownloadModule(
    val module: Module,
    val version: String,
    val perRepositoryErrors: Seq[String]
  ) extends Simple(
    s"Error downloading $module:$version\n" +
      perRepositoryErrors.map("  " + _.replace("\n", "  \n")).mkString("\n")
  )

  // Warning: currently, all conflicts in a resolution end-up in the same ConflictingDependencies instance
  final class ConflictingDependencies(
    dependencies: Set[Dependency]
  ) extends Simple(
    "Conflicting dependencies:\n" +
      Print.dependenciesUnknownConfigs(
        dependencies.toVector,
        Map.empty,
        printExclusions = false
      )
  )

  sealed abstract class Simple(message: String, cause: Throwable = null)
    extends ResolutionError(message, cause)

  final class Several(val head: Simple, val tail: Seq[Simple]) extends ResolutionError(
    (head +: tail).map(_.getMessage).mkString("\n")
  )

  def from(head: ResolutionError, tail: ResolutionError*): ResolutionError = {
    val errors = (head +: tail).flatMap {
      case s: Simple => Seq(s)
      case l: Several => l.head +: l.tail
    }
    assert(errors.nonEmpty)
    if (errors.tail.isEmpty)
      errors.head
    else
      new Several(errors.head, errors.tail)
  }

  abstract class UnsatisfiableRule(
    val rule: Rule,
    val conflict: UnsatisfiedRule,
    message: String
  ) extends Simple(s"Unsatisfiable rule $rule: $message", conflict)


}
