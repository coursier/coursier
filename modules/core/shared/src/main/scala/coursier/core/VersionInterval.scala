package coursier.core

import dataclass.data

@data class VersionInterval(
  from: Option[Version],
  to: Option[Version],
  fromIncluded: Boolean,
  toIncluded: Boolean
) {

  def isValid: Boolean = {
    val fromToOrder =
      for {
        f <- from
        t <- to
        cmd = f.compare(t)
      } yield cmd < 0 || (cmd == 0 && fromIncluded && toIncluded)

    fromToOrder.forall(x => x) && (from.nonEmpty || !fromIncluded) && (to.nonEmpty || !toIncluded)
  }

  def contains(version: Version): Boolean = {
    val fromCond =
      from.forall { from0 =>
        val cmp = from0.compare(version)
        cmp < 0 || cmp == 0 && fromIncluded
      }
    lazy val toCond =
      to.forall { to0 =>
        val cmp = version.compare(to0)
        cmp < 0 || cmp == 0 && toIncluded
      }

    fromCond && toCond
  }

  def merge(other: VersionInterval): Option[VersionInterval] = {
    val (newFrom, newFromIncluded) =
      (from, other.from) match {
        case (Some(a), Some(b)) =>
          val cmp = a.compare(b)
          if (cmp < 0) (Some(b), other.fromIncluded)
          else if (cmp > 0) (Some(a), fromIncluded)
          else (Some(a), fromIncluded && other.fromIncluded)

        case (Some(a), None) => (Some(a), fromIncluded)
        case (None, Some(b)) => (Some(b), other.fromIncluded)
        case (None, None) => (None, false)
      }

    val (newTo, newToIncluded) =
      (to, other.to) match {
        case (Some(a), Some(b)) =>
          val cmp = a.compare(b)
          if (cmp < 0) (Some(a), toIncluded)
          else if (cmp > 0) (Some(b), other.toIncluded)
          else (Some(a), toIncluded && other.toIncluded)

        case (Some(a), None) => (Some(a), toIncluded)
        case (None, Some(b)) => (Some(b), other.toIncluded)
        case (None, None) => (None, false)
      }

    Some(VersionInterval(newFrom, newTo, newFromIncluded, newToIncluded))
      .filter(_.isValid)
  }

  def constraint: VersionConstraint =
    this match {
      case VersionInterval.zero => VersionConstraint.all
      case itv =>
        (itv.from, itv.to, itv.fromIncluded, itv.toIncluded) match {
          case (Some(version), None, true, false) =>
            VersionConstraint.preferred(version)
          case _ =>
            VersionConstraint.interval(itv)
        }
    }

  def repr: String = Seq(
    if (fromIncluded) "[" else "(",
    from.map(_.repr).mkString,
    ",",
    to.map(_.repr).mkString,
    if (toIncluded) "]" else ")"
  ).mkString
}

object VersionInterval {
  val zero = VersionInterval(None, None, fromIncluded = false, toIncluded = false)
}
