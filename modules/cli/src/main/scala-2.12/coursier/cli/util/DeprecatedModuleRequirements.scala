package coursier.cli.util

import coursier.core.{Dependency, ModuleName, Organization}

final case class DeprecatedModuleRequirements(
  globalExcludes: Set[(Organization, ModuleName)],
  localExcludes: Map[String, Set[(Organization, ModuleName)]]
) {
  def apply(dep: Dependency): Dependency =
    dep.copy(
      exclusions = localExcludes.getOrElse(dep.module.orgName, dep.exclusions) | globalExcludes
    )
  def apply(deps: Seq[(Dependency, Map[String, String])]): Seq[(Dependency, Map[String, String])] =
    deps.map {
      case (d, p) => (apply(d), p)
    }
}
