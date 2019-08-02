package coursier.core

/**
 * Represents a reconciliation strategy given a dependency conflict.
 */
sealed abstract class Reconciliation {
  /**
   * Reconcile multiple version candidate.
   *
   * Returns `None` in case of conflict.
   */
  def apply(versions: Seq[String]): Option[String]
}

object Reconciliation {
  private final val LatestIntegration = "latest.integration"
  private final val LatestRelease = "latest.release"
  private final val LatestStable = "latest.stable"

  private def splitStandard(versions: Seq[String]): (Seq[String], Seq[String]) =
    versions.distinct.partition {
      case LatestIntegration => false
      case LatestRelease     => false
      case LatestStable      => false
      case _                 => true
    }

  private def retainLatestOpt(latests: Seq[String]): Option[String] =
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
    def apply(versions: Seq[String]): Option[String] = {
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else {
            val parsedConstraints = standard.map(Parse.versionConstraint)
            VersionConstraint.merge(parsedConstraints: _*)
              .flatMap(_.repr)
          }
        val retainedLatestOpt = retainLatestOpt(latests)

        if (standard.isEmpty)
          retainedLatestOpt
        else if (latests.isEmpty)
          retainedStandard
        else {
          val parsedIntervals = standard.map(Parse.versionConstraint)
            .filter(_.preferred.isEmpty) // only keep intervals
            .filter(_.interval != VersionInterval.zero) // not interval matching any version

          if (parsedIntervals.isEmpty)
            retainedLatestOpt
          else
            VersionConstraint.merge(parsedIntervals: _*)
              .flatMap(_.repr)
              .map(itv => (itv +: retainedLatestOpt.toSeq).mkString("&"))
        }
      }
    }
  }

  case object Relaxed extends Reconciliation {
    def apply(versions: Seq[String]): Option[String] = {
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else {
            val parsedConstraints = standard.map(Parse.versionConstraint)
            VersionConstraint.merge(parsedConstraints: _*)
              .getOrElse(VersionConstraint.relaxedMerge(parsedConstraints: _*))
              .repr
          }
        val retainedLatestOpt = retainLatestOpt(latests)
        if (latests.isEmpty)
          retainedStandard
        else
          retainedLatestOpt
      }
    }
  }
}
