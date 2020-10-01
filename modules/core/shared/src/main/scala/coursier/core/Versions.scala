package coursier.core

import dataclass.data

@data class Versions(
  latest: String,
  release: String,
  available: List[String],
  lastUpdated: Option[Versions.DateTime]
) {
  private def latestIntegrationCandidates(): Iterator[String] = {

    val latestOpt = Some(latest).filter(_.nonEmpty)
    val releaseOpt = Some(release).filter(_.nonEmpty)
    def latestFromAvailable = available
      .filter(v => !latestOpt.contains(v))
      .filter(v => !releaseOpt.contains(v))
      .map(Version(_))
      .sorted
      .distinct
      .reverseIterator
      .map(_.repr)

    latestOpt.iterator ++ releaseOpt.iterator ++ latestFromAvailable
  }
  private def latestReleaseCandidates(): Iterator[String] = {

    val latestOpt = Some(latest).filter(_.nonEmpty).filter(!_.endsWith("SNAPSHOT"))
    val releaseOpt = Some(release).filter(_.nonEmpty)
    def latestFromAvailable = available
      .filter(!_.endsWith("SNAPSHOT"))
      .filter(v => !releaseOpt.contains(v))
      .filter(v => !latestOpt.contains(v))
      .map(Version(_))
      .sorted
      .distinct
      .reverseIterator
      .map(_.repr)

    releaseOpt.iterator ++ latestOpt.iterator ++ latestFromAvailable
  }

  private def latestStableCandidates(): Iterator[String] = {

    def isStable(ver: String): Boolean =
      !ver.endsWith("SNAPSHOT") &&
      !ver.exists(_.isLetter) &&
        ver
          .split(Array('.', '-'))
          .forall(_.lengthCompare(5) <= 0)

    val latestOpt = Some(latest).filter(_.nonEmpty).filter(isStable)
    val releaseOpt = Some(release).filter(_.nonEmpty).filter(isStable)
    def latestFromAvailable = available
      .filter(isStable)
      .filter(v => !releaseOpt.contains(v))
      .filter(v => !latestOpt.contains(v))
      .map(Version(_))
      .sorted
      .distinct
      .reverseIterator
      .map(_.repr)

    releaseOpt.iterator ++ latestOpt.iterator ++ latestFromAvailable
  }

  def candidates(kind: Latest): Iterator[String] =
    kind match {
      case Latest.Integration => latestIntegrationCandidates()
      case Latest.Release => latestReleaseCandidates()
      case Latest.Stable => latestStableCandidates()
    }

  def latest(kind: Latest): Option[String] = {
    val it = candidates(kind)
    if (it.hasNext)
      Some(it.next())
    else
      None
  }

  def candidatesInInterval(itv: VersionInterval): Iterator[String] = {
    val fromRelease = Some(Version(release)).filter(itv.contains).map(_.repr)
    def fromAvailable = available
      .map(Version(_))
      .filter(itv.contains)
      .filter(v => !fromRelease.contains(v))
      .sorted
      .distinct
      .reverseIterator
      .map(_.repr)

    fromRelease.iterator ++ fromAvailable
  }
  def inInterval(itv: VersionInterval): Option[String] = {
    val it = candidatesInInterval(itv)
    if (it.hasNext)
      Some(it.next())
    else
      None
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
