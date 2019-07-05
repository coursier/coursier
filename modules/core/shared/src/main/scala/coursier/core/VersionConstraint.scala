package coursier.core

final case class VersionConstraint(
  interval: VersionInterval,
  preferred: Seq[Version]
) {
  def isValid: Boolean =
    interval.isValid && preferred.forall { v =>
      interval.contains(v) ||
        interval.to.forall(v.compare(_) <= 0)
    }

  def blend: Option[Either[VersionInterval, Version]] =
    if (isValid) {
      val preferredInInterval = preferred.filter(interval.contains)

      if (preferredInInterval.isEmpty)
        Some(Left(interval))
      else
        Some(Right(preferredInInterval.max))
    } else
      None

  def repr: Option[String] =
    blend.map {
      case Left(itv) =>
        if (itv == VersionInterval.zero)
          ""
        else
          itv.repr
      case Right(v) => v.repr
    }
}

object VersionConstraint {

  def preferred(version: Version): VersionConstraint =
    VersionConstraint(VersionInterval.zero, Seq(version))
  def interval(interval: VersionInterval): VersionConstraint =
    VersionConstraint(interval, Nil)

  val all = VersionConstraint(VersionInterval.zero, Nil)

  def merge(constraints: VersionConstraint*): Option[VersionConstraint] = {

    val intervals = constraints.map(_.interval)

    val intervalOpt =
      (Option(VersionInterval.zero) /: intervals) {
        case (acc, itv) =>
          acc.flatMap(_.merge(itv))
      }

    for (interval <- intervalOpt) yield {
      val preferreds = constraints.flatMap(_.preferred).distinct
      VersionConstraint(interval, preferreds)
    }
  }
}
