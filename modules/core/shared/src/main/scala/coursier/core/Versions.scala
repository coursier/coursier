package coursier.core

import dataclass.data

@data class Versions(
  latest: String,
  release: String,
  available: List[String],
  lastUpdated: Option[Versions.DateTime]
) {
  private def latestIntegrationOpt: Option[String] = {

    val latestOpt = Some(latest).filter(_.nonEmpty)
    val releaseOpt = Some(release).filter(_.nonEmpty)
    val latestFromAvailable = Some(available)
      .filter(_.nonEmpty)
      .map(_.map(Version(_)).max.repr)

    latestOpt
      .orElse(releaseOpt)
      .orElse(latestFromAvailable)
  }
  private def latestReleaseOpt: Option[String] = {

    val latestOpt = Some(latest).filter(_.nonEmpty).filter(!_.endsWith("SNAPSHOT"))
    val releaseOpt = Some(release).filter(_.nonEmpty)
    val latestFromAvailable = Some(available)
      .map(_.filter(!_.endsWith("SNAPSHOT")))
      .filter(_.nonEmpty)
      .map(_.map(Version(_)).max.repr)

    releaseOpt
      .orElse(latestOpt)
      .orElse(latestFromAvailable)
  }

  private def latestStableOpt: Option[String] = {

    def isStable(ver: String): Boolean =
      !ver.endsWith("SNAPSHOT") &&
        ver
          .split(Array('.', '-'))
          .forall(_.lengthCompare(5) <= 0)

    val latestOpt = Some(latest).filter(_.nonEmpty).filter(isStable)
    val releaseOpt = Some(release).filter(_.nonEmpty).filter(isStable)
    val latestFromAvailable = Some(available)
      .map(_.filter(isStable))
      .filter(_.nonEmpty)
      .map(_.map(Version(_)).max.repr)

    releaseOpt
      .orElse(latestOpt)
      .orElse(latestFromAvailable)
  }

  def latest(kind: Latest): Option[String] =
    kind match {
      case Latest.Integration => latestIntegrationOpt
      case Latest.Release => latestReleaseOpt
      case Latest.Stable => latestStableOpt
    }

  def inInterval(itv: VersionInterval): Option[String] = {
    val release0 = Version(release)

    if (itv.contains(release0))
      Some(release)
    else {
      val inInterval = available
        .map(Version(_))
        .filter(itv.contains)

      if (inInterval.isEmpty) None
      else Some(inInterval.max.repr)
    }
  }
}

object Versions {
  @data class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  ) extends Ordered[DateTime] {
    def compare(other: DateTime): Int = {
      import Ordering.Implicits._
      if (this == other) 0
      else if (tuple < other.tuple) -1
      else 1
    }
  }

  val empty = Versions("", "", Nil, None)
}
