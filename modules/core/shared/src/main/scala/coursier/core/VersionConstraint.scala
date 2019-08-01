package coursier.core

final case class VersionConstraint(
  interval: VersionInterval,
  preferred: Seq[Version]
) {
  def isValid: Boolean =
    interval.isValid && preferred.forall { v =>
      interval.contains(v) ||
        interval.to.forall { to =>
          val cmp = v.compare(to)
          cmp < 0 || (cmp == 0 && interval.toIncluded)
        }
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

    val constraintOpt = intervalOpt.map { interval =>
      val preferreds = constraints.flatMap(_.preferred).distinct
      VersionConstraint(interval, preferreds)
    }

    constraintOpt.filter(_.isValid)
  }

  // 1. sort constraints in ascending order.
  // 2. from the right, merge them two-by-two with the merge method above
  // 3. return the last successful merge
  def relaxedMerge(constraints: VersionConstraint*): Option[VersionConstraint] = {
    merge(constraints: _*) orElse {
      def mergeByTwo(head: VersionConstraint, rest: List[VersionConstraint]): VersionConstraint = {
        rest match {
          case next :: xs =>
            merge(head, next) match {
              case Some(success) => mergeByTwo(success, xs)
              case _             => head
            }
          case Nil => head
        }
      }
      val cs = constraints.toList
      if (cs.isEmpty) None
      else if (cs.size == 1) cs.headOption
      else {
        val sorted = cs.sortBy(c =>
          if (c.preferred.nonEmpty) (c.preferred.head: Version)
          else if (c.interval.fromIncluded && c.interval.from.isDefined) c.interval.from.get
          else Version("0")
        )
        val reversed = sorted.reverse
        reversed match {
          case x :: xs => Some(mergeByTwo(x, xs))
          case _       => None
        }
      }
    }
  }
}
