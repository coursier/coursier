package coursier.cli.util

import coursier.core.{Dependency, MinimizedExclusions, ModuleName, Organization}
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}

final case class DeprecatedModuleRequirements0(
  globalExcludes: Set[JavaOrScalaModule],
  localExcludes: Map[JavaOrScalaModule, Set[JavaOrScalaModule]]
) {
  def apply(dep: JavaOrScalaDependency): JavaOrScalaDependency =
    dep.addExclude((localExcludes.getOrElse(dep.module, dep.exclude) | globalExcludes).toSeq: _*)
  def apply(
    deps: Seq[(JavaOrScalaDependency, Map[String, String])]
  ): Seq[(JavaOrScalaDependency, Map[String, String])] =
    deps.map {
      case (d, p) => (apply(d), p)
    }
}
