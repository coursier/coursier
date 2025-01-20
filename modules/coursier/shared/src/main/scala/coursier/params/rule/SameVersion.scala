package coursier.params.rule

import coursier.core.{Module, Resolution}
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule
import coursier.util.ModuleMatcher
import coursier.version.{Version, VersionConstraint}
import dataclass.data

import scala.collection.compat._

/** Forces some modules to all have the same version.
  *
  * If ever different versions are found, the highest one is currently selected.
  */
@data class SameVersion(matchers: Set[ModuleMatcher]) extends Rule {

  import SameVersion._

  type C = SameVersionConflict

  def check(res: Resolution): Option[SameVersionConflict] = {

    val deps =
      res.dependenciesWithRetainedVersions.filter(dep => matchers.exists(_.matches(dep.module)))
    val modules  = deps.map(_.module)
    val versions = deps.map(_.versionConstraint)

    if (versions.size <= 1)
      None
    else
      Some(new SameVersionConflict(this, modules, versions))
  }

  def tryResolve(
    res: Resolution,
    conflict: SameVersionConflict
  ): Either[UnsatisfiableRule, Resolution] = {

    val deps =
      res.dependencies.collect {
        case dep if matchers.exists(_.matches(dep.module)) =>
          (
            dep,
            res.retainedVersions.getOrElse(
              dep.module,
              sys.error(s"${dep.module} not found in retained versions")
            )
          )
      }
    val versions = deps.map(_._2)
    assert(deps.nonEmpty)
    assert(versions.size > 1)

    val selectedVersion = versions.max

    // add stuff to root dependencies instead of forcing versions?

    val cantForce = res
      .forceVersions0
      .view
      .filterKeys(conflict.modules)
      .filter(_._2 != selectedVersion)
      .iterator
      .toMap

    if (cantForce.isEmpty) {
      val res0 = res.withForceVersions0 {
        res.forceVersions0 ++
          conflict.modules.toSeq.map(m => m -> VersionConstraint.fromVersion(selectedVersion))
      }
      Right(res0)
    }
    else {
      val c = new CantForceSameVersion(
        res,
        this,
        cantForce.keySet,
        selectedVersion,
        conflict
      )
      Left(c)
    }
  }
}

object SameVersion {

  def apply(module: Module, other: Module*): SameVersion =
    SameVersion((other.toSet + module).map(ModuleMatcher(_)))

  final class SameVersionConflict(
    override val rule: SameVersion,
    val modules: Set[Module],
    val foundVersions: Set[VersionConstraint]
  ) extends UnsatisfiedRule(
        rule,
        s"Found versions ${foundVersions.toVector.sorted.map(_.asString).mkString(", ")} " +
          s"for ${modules.toVector.map(_.toString).sorted.mkString(", ")}"
      ) {
    require(foundVersions.size > 1)
  }

  final class CantForceSameVersion(
    resolution: Resolution,
    override val rule: SameVersion,
    modules: Set[Module],
    version: Version,
    conflict: SameVersionConflict
  ) extends UnsatisfiableRule(
        resolution,
        rule,
        conflict,
        // FIXME More detailed message? (say why it can't be forced)
        s"Can't force version ${version.asString} for modules ${modules.toVector.map(_.toString).mkString(", ")}"
      ) {
    assert(modules.nonEmpty)
    assert(modules.forall(conflict.modules))
  }

}
