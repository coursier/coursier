package coursier.parse

import coursier.core.{Module, ModuleName, Organization, Reconciliation}
import coursier.util.{ModuleMatcher, ModuleMatchers, ValidationNel}
import coursier.util.Traverse._

object ReconciliationParser {
  def reconciliation(input: Seq[String], scalaVersionOrDefault: String): ValidationNel[String, Seq[(ModuleMatchers, Reconciliation)]] = {
    ValidationNel.fromEither(
      DependencyParser.moduleVersions(
        input, scalaVersionOrDefault
      ).either match {
        case Left(e) => Left(e.mkString("\n"))
        case Right(elems) =>
          val rs = (elems.validationNelTraverse { case (m: Module, v: String) =>
            reconciliation(m, v)
          }).either
          rs match {
            case Right(x) => Right(x)
            case Left(e)  => Left(e.mkString("\n"))
          }
      })
  }

  private def reconciliation(module: Module, v: String): ValidationNel[String, (ModuleMatchers, Reconciliation)] = {
    val m =
      if (module.organization == Organization("*") && module.name == ModuleName("*")) ModuleMatchers.all
      else ModuleMatchers(exclude = Set(ModuleMatcher.all), include = Set(ModuleMatcher(module)))
    ValidationNel.fromEither((v match {
        case "basic"   => Right(Reconciliation.Basic)
        case "relaxed" => Right(Reconciliation.Relaxed)
        case _         => Left(s"Unknown reconciliation '$v'")
      }) match {
        case Left(e)  => Left(e)
        case Right(r) => Right(m -> r)
      })
  }
}
