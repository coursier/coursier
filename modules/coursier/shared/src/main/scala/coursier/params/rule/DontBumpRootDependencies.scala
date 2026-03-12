package coursier.params.rule

import coursier.core.{Dependency, Module, Resolution}
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule
import coursier.util.ModuleMatchers
import coursier.version.VersionConstraint
import dataclass.data

@data class DontBumpRootDependencies(matchers: ModuleMatchers) extends Rule {

  import DontBumpRootDependencies._

  type C = BumpedRootDependencies

  def check(res: Resolution): Option[BumpedRootDependencies] = {

    val bumped = res
      .rootDependencies
      .iterator
      .filter { rootDep =>
        // "any" version substitution is not a bump
        rootDep.versionConstraint.asString.nonEmpty &&
        matchers.matches(rootDep.module)
      }
      .map { rootDep =>
        val selected = res.retainedVersions
          .getOrElse(rootDep.module, sys.error(s"${rootDep.module} not found in retained versions"))
        rootDep -> selected
      }
      .filter {
        case (dep, selectedVer) =>
          if (dep.versionConstraint.preferred.nonEmpty)
            !dep.versionConstraint.preferred.contains(selectedVer)
          else
            !dep.versionConstraint.interval.contains(selectedVer)
      }
      .map {
        case (dep, selectedVer) =>
          (dep, selectedVer.repr)
      }
      .toVector

    if (bumped.isEmpty)
      None
    else
      Some(new BumpedRootDependencies(bumped, this))
  }

  def tryResolve(
    res: Resolution,
    conflict: BumpedRootDependencies
  ): Either[UnsatisfiableRule, Resolution] = {

    val modules = conflict
      .bumpedRootDependencies
      .map {
        case (dep, _) =>
          dep.module -> dep.versionConstraint
      }
      .toMap
    val cantForce = res
      .forceVersions0
      .filter {
        case (mod, ver) =>
          modules.get(mod).exists(_ != ver)
      }

    if (cantForce.isEmpty) {
      val res0 = res.withForceVersions0(res.forceVersions0 ++ modules)
      Right(res0)
    }
    else {
      val c = new CantForceRootDependencyVersions(res, cantForce, conflict, this)
      Left(c)
    }
  }

}

object DontBumpRootDependencies {

  def apply(): DontBumpRootDependencies =
    DontBumpRootDependencies(ModuleMatchers.all)

  final class BumpedRootDependencies(
    val bumpedRootDependencies: Seq[(Dependency, String)],
    override val rule: DontBumpRootDependencies
  ) extends UnsatisfiedRule(
        rule,
        s"Some root dependency versions were bumped: " +
          bumpedRootDependencies.map(d => s"${d._1.module}:${d._1.versionConstraint.asString}")
            .toVector
            .sorted
            .mkString(", ")
      ) {
    require(bumpedRootDependencies.nonEmpty)
  }

  final class CantForceRootDependencyVersions(
    resolution: Resolution,
    cantBump: Map[Module, VersionConstraint],
    conflict: BumpedRootDependencies,
    override val rule: DontBumpRootDependencies
  ) extends UnsatisfiableRule(
        resolution,
        rule,
        conflict, {
          val mods = cantBump
            .toVector
            .map {
              case (k, v) =>
                s"$k ($v)"
            }
            .sorted
            .mkString(", ")
          // FIXME More detailed message? (say why it can't be forced)
          s"Can't force version of modules $mods"
        }
      ) {
    assert(cantBump.nonEmpty)
  }

}
