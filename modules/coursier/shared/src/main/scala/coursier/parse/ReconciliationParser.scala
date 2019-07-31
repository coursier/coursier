package coursier.parse

import coursier.core.{Module, ModuleName, Organization, Reconciliation}
import coursier.util.{ModuleMatcher, ModuleMatchers, ValidationNel}
import coursier.util.Traverse._

object ReconciliationParser {
  def reconciliation(input: Seq[String], scalaVersionOrDefault: String): Either[List[String], Seq[(ModuleMatchers, Reconciliation)]] = {
    DependencyParser.moduleVersions(
      input, scalaVersionOrDefault
    ).either match {
      case Left(e) => Left(e)
      case Right(elems) =>
        (elems.validationNelTraverse { case (m: Module, v: String) =>
          val e = reconciliation(m, v)
          ValidationNel.fromEither(e)
        }).either
    }
  }

  def reconciliation(module: Module, v: String): Either[String, (ModuleMatchers, Reconciliation)] = {
    val m =
      if (module.organization == Organization("*") && module.name == ModuleName("*")) ModuleMatchers.all
      else ModuleMatchers(exclude = Set(ModuleMatcher.all), include = Set(ModuleMatcher(module)))
    (v match {
      case "basic"   => Right(Reconciliation.Basic)
      case "relaxed" => Right(Reconciliation.Relaxed)
      case _         => Left(s"Unknown reconciliation '$v'")
    }) match {
      case Left(e)  => Left(e)
      case Right(r) => Right(m -> r)
    }
  }
}
