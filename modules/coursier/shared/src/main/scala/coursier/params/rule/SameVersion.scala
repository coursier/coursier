package coursier.params.rule

import coursier.core.{Module, Resolution, Version}
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule

/**
  * Forces some modules to all have the same version.
  *
  * If ever different versions are found, the highest one is currently selected.
  */
final case class SameVersion(modules: Set[Module]) extends Rule {

  import SameVersion._

  type C = SameVersionConflict

  def check(res: Resolution): Option[SameVersionConflict] = {

    val deps = res.dependenciesWithSelectedVersions.filter(dep => modules.contains(dep.module))
    val versions = deps.map(_.version)

    if (versions.size <= 1)
      None
    else
      Some(new SameVersionConflict(this, versions))
  }

  def tryResolve(
    res: Resolution,
    conflict: SameVersionConflict
  ): Either[UnsatisfiableRule, Resolution] = {

    val deps = res.dependenciesWithSelectedVersions.filter(dep => modules.contains(dep.module))
    val versions = deps.map(_.version)
    assert(deps.nonEmpty)
    assert(versions.size > 1)

    val selectedVersion = versions.maxBy(Version(_))

    // add stuff to root dependencies instead of forcing versions?

    val cantForce = res
      .forceVersions
      .filterKeys(modules)
      .filter(_._2 != selectedVersion)
      .iterator
      .toMap

    if (cantForce.isEmpty) {

      val res0 = res.copy(
        forceVersions = res.forceVersions ++ modules.toSeq.map(m => m -> selectedVersion)
      )

      Right(res0)
    } else {
      val c = new CantForceSameVersion(
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
    SameVersion(other.toSet + module)

  final class SameVersionConflict(
    override val rule: SameVersion,
    val foundVersions: Set[String]
  ) extends UnsatisfiedRule(
    rule,
    s"Found versions ${foundVersions.toVector.sorted.mkString(", ")} " +
      s"for ${rule.modules.toVector.map(_.toString).sorted.mkString(", ")}"
  ) {
    require(foundVersions.size > 1)
  }

  final class CantForceSameVersion(
    override val rule: SameVersion,
    modules: Set[Module],
    version: String,
    conflict: SameVersionConflict
  ) extends UnsatisfiableRule(
    rule,
    conflict,
    // FIXME More detailed message? (say why it can't be forced)
    s"Can't force version $version for modules ${modules.toVector.map(_.toString).mkString(", ")}"
  ) {
    assert(modules.nonEmpty)
    assert(modules.forall(rule.modules))
  }

}
