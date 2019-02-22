package coursier.params.rule

import coursier.core.{Dependency, Module, Parse, Resolution, Version}
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule

case object DontBumpRootDependencies extends Rule {

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
        case (dep, selectedVer) =>
          val wanted = Parse.versionConstraint(dep.version)
          !wanted.interval.contains(selectedVer) && !wanted.preferred.contains(selectedVer)
      }
      .map {
        case (dep, selectedVer) =>
          (dep, selectedVer.repr)
      }
      .toVector

    if (bumped.isEmpty)
      None
    else
      Some(new BumpedRootDependencies(bumped))
  }

  def tryResolve(res: Resolution, conflict: BumpedRootDependencies): Either[UnsatisfiableRule, Resolution] = {

    val modules = conflict.bumpedRootDependencies.map { case (dep, v) => dep.module -> v }.toMap
    val cantForce = res
      .forceVersions
      .filter {
        case (mod, ver) =>
          modules.get(mod).exists(_ != ver)
      }

    if (cantForce.isEmpty) {

      val res0 = res.copy(
        forceVersions = res.forceVersions ++ modules
      )

      Right(res0)
    } else {
      val c = new CantForceRootDependencyVersions(res, cantForce, conflict)
      Left(c)
    }
  }

  final class BumpedRootDependencies(
    val bumpedRootDependencies: Seq[(Dependency, String)],
    override val rule: DontBumpRootDependencies.type = DontBumpRootDependencies
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
    override val rule: DontBumpRootDependencies.type = DontBumpRootDependencies
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
