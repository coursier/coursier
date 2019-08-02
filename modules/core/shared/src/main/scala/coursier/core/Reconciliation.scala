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
  private[coursier] final val LatestIntegration = "latest.integration"
  private[coursier] final val LatestRelease = "latest.release"
  private[coursier] final val LatestStable = "latest.stable"

  abstract class AbstractReconciliation extends Reconciliation {
    protected def mergeVersionConstraints(constraints: Seq[VersionConstraint]): Option[VersionConstraint]

    protected def splitStandard(versions: Seq[String]): (Seq[String], Seq[String]) =
      versions.distinct.partition {
        case LatestIntegration => false
        case LatestRelease     => false
        case LatestStable      => false
        case _                 => true
      }

    protected def retainLatestOpt(latests: Seq[String]): Option[String] =
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

    protected def retainStandardOpt(standard: Seq[String]): Option[String] =
      if (standard.isEmpty) None
      else if (standard.lengthCompare(1) == 0) standard.headOption
      else {
        val parsedConstraints = standard.map(Parse.versionConstraint)
        mergeVersionConstraints(parsedConstraints)
          .flatMap(_.repr)
      }
  }

  /**
   * Implements the basic reconciliation rule based on `VersionConstraint.merge`.
   */
  case object Basic extends AbstractReconciliation {
    /**
     * Reconcile multiple version candidate.
     *
     * Returns `None` in case of conflict.
     */
    override def apply(versions: Seq[String]): Option[String] = {
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard = retainStandardOpt(standard)
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
            mergeVersionConstraints(parsedIntervals)
              .flatMap(_.repr)
              .map(itv => (itv +: retainedLatestOpt.toSeq).mkString("&"))
        }
      }
    }

    override protected def mergeVersionConstraints(constraints: Seq[VersionConstraint]): Option[VersionConstraint] =
      VersionConstraint.merge(constraints: _*)
  }

  /**
   * Implements the relaxed reconciliation.
   */
  case object Relaxed extends AbstractReconciliation {
    /**
     * Reconcile multiple version candidate.
     *
     * Returns `None` in case of conflict.
     */
    override def apply(versions: Seq[String]): Option[String] = {
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard = retainStandardOpt(standard)
        val retainedLatestOpt = retainLatestOpt(latests)
        if (latests.isEmpty)
          retainedStandard
        else
          retainedLatestOpt
      }
    }

    override protected def mergeVersionConstraints(constraints: Seq[VersionConstraint]): Option[VersionConstraint] =
      VersionConstraint.relaxedMerge(constraints: _*)
  }
}
