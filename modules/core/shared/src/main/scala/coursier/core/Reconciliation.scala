package coursier.core

import coursier.version.{
  VersionConstraint => VersionConstraint0,
  VersionInterval => VersionInterval0
}

/** Represents a reconciliation strategy given a dependency conflict.
  */
@deprecated("Use coursier.version.ConstraintReconciliation instead", "2.1.25")
sealed abstract class Reconciliation {

  /** Reconcile multiple version candidate.
    *
    * Returns `None` in case of conflict.
    */
  def apply(versions: Seq[VersionConstraint0]): Option[VersionConstraint0]

  def reconcile(versions: Seq[VersionConstraint0]): Option[VersionConstraint0] =
    apply(versions)

  def id: String
}

@deprecated("Use coursier.version.ConstraintReconciliation instead", "2.1.25")
object Reconciliation {
  private final val LatestIntegration = VersionConstraint0("latest.integration")
  private final val LatestRelease     = VersionConstraint0("latest.release")
  private final val LatestStable      = VersionConstraint0("latest.stable")

  private def splitStandard(versions: Seq[VersionConstraint0])
    : (Seq[VersionConstraint0], Seq[VersionConstraint0]) =
    versions.distinct.partition {
      case LatestIntegration => false
      case LatestRelease     => false
      case LatestStable      => false
      case _                 => true
    }

  private def retainLatestOpt(latests: Seq[VersionConstraint0]): Option[VersionConstraint0] =
    if (latests.isEmpty) None
    else if (latests.lengthCompare(1) == 0) latests.headOption
    else {
      val set = latests.toSet
      val retained =
        if (set(LatestIntegration))
          LatestIntegration
        else if (set(LatestRelease))
          LatestRelease
        else {
          // at least two distinct latest.* means we shouldn't even reach this else block anyway
          assert(set(LatestStable))
          LatestStable
        }
      Some(retained)
    }

  case object Default extends Reconciliation {
    def apply(versions: Seq[VersionConstraint0]): Option[VersionConstraint0] =
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else
            VersionConstraint0.merge(standard: _*).map(_.uniquePreferred.removeUnusedPreferred)
        val retainedLatestOpt = retainLatestOpt(latests)

        if (standard.isEmpty)
          retainedLatestOpt
        else if (latests.isEmpty)
          retainedStandard
        else {
          val parsedIntervals = standard
            .filter(_.preferred.isEmpty)                 // only keep intervals
            .filter(_.interval != VersionInterval0.zero) // not interval matching any version

          if (parsedIntervals.isEmpty)
            retainedLatestOpt
          else
            VersionConstraint0.merge(parsedIntervals: _*)
              .map(_.uniquePreferred.removeUnusedPreferred)
        }
      }

    def id: String = "default"
  }

  case object Relaxed extends Reconciliation {
    def apply(versions: Seq[VersionConstraint0]): Option[VersionConstraint0] =
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else
            Some(
              VersionConstraint0.merge(standard: _*)
                .getOrElse(VersionConstraint0.relaxedMerge(standard: _*))
                .uniquePreferred
                .removeUnusedPreferred
            )
        val retainedLatestOpt = retainLatestOpt(latests)
        if (latests.isEmpty) retainedStandard
        else retainedLatestOpt
      }

    def id: String = "relaxed"
  }

  /** Strict version reconciliation.
    *
    * This particular instance behaves the same as [[Default]] when used by
    * [[coursier.core.Resolution]]. Actual strict conflict manager is handled by
    * `coursier.params.rule.Strict`, which is set up by `coursier.Resolve` when a strict
    * reconciliation is added to it.
    */
  case object Strict extends Reconciliation {
    def apply(versions: Seq[VersionConstraint0]): Option[VersionConstraint0] =
      Default(versions)

    def id: String = "strict"
  }

  /** Semantic versioning version reconciliation.
    *
    * This particular instance behaves the same as [[Default]] when used by
    * [[coursier.core.Resolution]]. Actual semantic versioning checks are handled by
    * `coursier.params.rule.Strict` with field `semVer = true`, which is set up by
    * `coursier.Resolve` when a SemVer reconciliation is added to it.
    */
  case object SemVer extends Reconciliation {
    def apply(versions: Seq[VersionConstraint0]): Option[VersionConstraint0] =
      Default(versions)

    def id: String = "semver"
  }

  def apply(input: VersionConstraint0): Option[Reconciliation] =
    apply(input.asString)

  def apply(input: String): Option[Reconciliation] =
    input match {
      case "default" => Some(Default)
      case "relaxed" => Some(Relaxed)
      case "strict"  => Some(Strict)
      case "semver"  => Some(SemVer)
      case _         => None
    }
}
