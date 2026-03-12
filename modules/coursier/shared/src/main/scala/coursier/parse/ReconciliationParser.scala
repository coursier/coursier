package coursier.parse

import coursier.core.{Module, ModuleName, Organization}
import coursier.util.{ModuleMatcher, ModuleMatchers, ValidationNel}
import coursier.util.Traverse._
import coursier.version.{ConstraintReconciliation, VersionConstraint}

object ReconciliationParser {
  def reconciliation0(
    input: Seq[String],
    scalaVersionOrDefault: String
  ): ValidationNel[String, Seq[(ModuleMatchers, ConstraintReconciliation)]] =
    DependencyParser.moduleVersions0(input, scalaVersionOrDefault).flatMap { elems =>
      elems.validationNelTraverse {
        case (m, v) =>
          ValidationNel.fromEither(reconciliation(m, v))
      }
    }

  @deprecated("Use reconciliation0 instead", "2.1.25")
  def reconciliation(
    input: Seq[String],
    scalaVersionOrDefault: String
  ): ValidationNel[String, Seq[(ModuleMatchers, coursier.core.Reconciliation)]] =
    reconciliation0(input, scalaVersionOrDefault).map(_.map {
      case (m, ConstraintReconciliation.Default) =>
        (m, coursier.core.Reconciliation.Default)
      case (m, ConstraintReconciliation.Relaxed) =>
        (m, coursier.core.Reconciliation.Relaxed)
      case (m, ConstraintReconciliation.Strict) =>
        (m, coursier.core.Reconciliation.Strict)
      case (m, ConstraintReconciliation.SemVer) =>
        (m, coursier.core.Reconciliation.SemVer)
    })

  private def reconciliation(
    module: Module,
    v: VersionConstraint
  ): Either[String, (ModuleMatchers, ConstraintReconciliation)] = {
    val m =
      if (module.organization == Organization("*") && module.name == ModuleName("*"))
        ModuleMatchers.all
      else ModuleMatchers(exclude = Set(ModuleMatcher.all), include = Set(ModuleMatcher(module)))
    ConstraintReconciliation(v.asString)
      .map(m -> _)
      .toRight(s"Unknown reconciliation '$v'")
  }
}
