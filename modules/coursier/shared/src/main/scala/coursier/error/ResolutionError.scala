package coursier.error

import coursier.core.{Dependency, Module, Resolution}
import coursier.error.conflict.UnsatisfiedRule
import coursier.params.rule.Rule
import coursier.util.Print

sealed abstract class ResolutionError(
  val resolution: Resolution,
  message: String,
  cause: Throwable = null
) extends CoursierError(message, cause) {
  def errors: Seq[ResolutionError.Simple]
}

object ResolutionError {

  final class MaximumIterationReached(resolution: Resolution) extends Simple(
    resolution,
    "Maximum number of iterations reached"
  )

  final class CantDownloadModule(
    resolution: Resolution,
    val module: Module,
    val version: String,
    val perRepositoryErrors: Seq[String]
  ) extends Simple(
    resolution,
    s"Error downloading $module:$version\n" +
      perRepositoryErrors.map("  " + _.replace("\n", "  \n")).mkString("\n")
  )

  // Warning: currently, all conflicts in a resolution end-up in the same ConflictingDependencies instance
  final class ConflictingDependencies(
    resolution: Resolution,
    val dependencies: Set[Dependency]
  ) extends Simple(
    resolution,
    "Conflicting dependencies:\n" +
      Print.dependenciesUnknownConfigs(
        dependencies.toVector,
        Map.empty,
        printExclusions = false,
        useFinalVersions = false
      )
  )

  sealed abstract class Simple(resolution: Resolution, message: String, cause: Throwable = null)
    extends ResolutionError(resolution, message, cause) {
    def errors: Seq[ResolutionError.Simple] =
      Seq(this)
  }

  final class Several(val head: Simple, val tail: Seq[Simple]) extends ResolutionError(
    head.resolution, (head +: tail).map(_.getMessage).mkString("\n")
  ) {
    def errors: Seq[ResolutionError.Simple] =
      head +: tail
  }

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
    resolution: Resolution,
    val rule: Rule,
    val conflict: UnsatisfiedRule,
    message: String
  ) extends Simple(resolution, message, conflict)


}
