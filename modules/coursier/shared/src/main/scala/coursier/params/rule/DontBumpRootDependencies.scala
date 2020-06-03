package coursier.params.rule

import coursier.core.{Dependency, Module, Parse, Resolution}
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule
import coursier.util.ModuleMatchers
import coursier.version.Version
import dataclass.data

@data class DontBumpRootDependencies(matchers: ModuleMatchers) extends Rule {

  import DontBumpRootDependencies._

  type C = BumpedRootDependencies

  def check(res: Resolution): Option[BumpedRootDependencies] = {

    val bumped = res
      .rootDependencies
      .iterator
      .map { rootDep =>
        val selected = Version(res.reconciledVersions.getOrElse(rootDep.module, rootDep.version))
        rootDep -> selected
      }
      .filter {
        case (dep, _) =>
          matchers.matches(dep.module)
      }
      .filter {
        case (dep, selectedVer) =>
          val wanted = Parse.versionConstraint(dep.version)
          if (wanted.preferred.nonEmpty)
            !wanted.preferred.contains(selectedVer)
          else
            !wanted.interval.contains(selectedVer)
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

  def tryResolve(res: Resolution, conflict: BumpedRootDependencies): Either[UnsatisfiableRule, Resolution] = {

    val modules = conflict.bumpedRootDependencies.map { case (dep, _) => dep.module -> dep.version }.toMap
    val cantForce = res
      .forceVersions
      .filter {
        case (mod, ver) =>
          modules.get(mod).exists(_ != ver)
      }

    if (cantForce.isEmpty) {
      val res0 = res.withForceVersions(res.forceVersions ++ modules)
      Right(res0)
    } else {
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
      bumpedRootDependencies.map(d => s"${d._1.module}:${d._1.version}").toVector.sorted.mkString(", ")
  ) {
    require(bumpedRootDependencies.nonEmpty)
  }

  final class CantForceRootDependencyVersions(
    resolution: Resolution,
    cantBump: Map[Module, String],
    conflict: BumpedRootDependencies,
    override val rule: DontBumpRootDependencies
  ) extends UnsatisfiableRule(
    resolution,
    rule,
    conflict,
    // FIXME More detailed message? (say why it can't be forced)
    s"Can't force version of modules ${cantBump.toVector.map { case (k, v) => s"$k ($v)" }.sorted.mkString(", ")}"
  ) {
    assert(cantBump.nonEmpty)
  }

}
