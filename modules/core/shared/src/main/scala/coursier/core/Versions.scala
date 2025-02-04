package coursier.core

import coursier.version.{Version => Version0, VersionInterval => VersionInterval0}
import dataclass.data

@data class Versions(
  latest0: Version0,
  release0: Version0,
  available0: List[Version0],
  lastUpdated: Option[Versions.DateTime]
) {

  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def this(
    latest: String,
    release: String,
    available: List[String],
    lastUpdated: Option[Versions.DateTime]
  ) =
    this(
      Version0(latest),
      Version0(release),
      available.map(Version0(_)),
      lastUpdated
    )

  @deprecated("Use latest0 instead", "2.1.25")
  def latest: String = latest0.asString
  @deprecated("Use release0 instead", "2.1.25")
  def release: String = release0.asString
  @deprecated("Use available0 instead", "2.1.25")
  def available: List[String] = available0.map(_.asString)

  @deprecated("Use withLatest0 instead", "2.1.25")
  def withLatest(newLatest: String): Versions =
    if (newLatest == latest) this
    else withLatest0(Version0(newLatest))
  @deprecated("Use withRelease0 instead", "2.1.25")
  def withRelease(newRelease: String): Versions =
    if (newRelease == release) this
    else withRelease0(Version0(newRelease))
  @deprecated("Use withAvailable0 instead", "2.1.25")
  def withAvailable(newAvailable: List[String]): Versions =
    if (newAvailable == available) this
    else withAvailable0(newAvailable.map(Version0(_)))

  private def latestIntegrationCandidates(): Iterator[Version0] = {

    val latestOpt  = Some(latest0).filter(_.repr.nonEmpty)
    val releaseOpt = Some(release0).filter(_.repr.nonEmpty)
    def latestFromAvailable = available0
      .filter(v => !latestOpt.contains(v))
      .filter(v => !releaseOpt.contains(v))
      .sorted
      .distinct
      .reverseIterator

    latestOpt.iterator ++ releaseOpt.iterator ++ latestFromAvailable
  }
  private def latestReleaseCandidates(): Iterator[Version0] = {

    val latestOpt  = Some(latest0).filter(_.repr.nonEmpty).filter(!_.repr.endsWith("SNAPSHOT"))
    val releaseOpt = Some(release0).filter(_.repr.nonEmpty)
    def latestFromAvailable = available0
      .filter(!_.repr.endsWith("SNAPSHOT"))
      .filter(v => !releaseOpt.contains(v))
      .filter(v => !latestOpt.contains(v))
      .sorted
      .distinct
      .reverseIterator

    releaseOpt.iterator ++ latestOpt.iterator ++ latestFromAvailable
  }

  private def latestStableCandidates(): Iterator[Version0] = {

    def isStable(ver: Version0): Boolean =
      !ver.repr.endsWith("SNAPSHOT") &&
      !ver.repr.exists(_.isLetter) &&
      ver
        .repr
        .split(Array('.', '-'))
        .forall(_.lengthCompare(5) <= 0)

    val latestOpt  = Some(latest0).filter(_.repr.nonEmpty).filter(isStable)
    val releaseOpt = Some(release0).filter(_.repr.nonEmpty).filter(isStable)
    def latestFromAvailable = available0
      .filter(isStable)
      .filter(v => !releaseOpt.contains(v))
      .filter(v => !latestOpt.contains(v))
      .sorted
      .distinct
      .reverseIterator

    releaseOpt.iterator ++ latestOpt.iterator ++ latestFromAvailable
  }

  def candidates0(kind: Latest): Iterator[Version0] =
    kind match {
      case Latest.Integration => latestIntegrationCandidates()
      case Latest.Release     => latestReleaseCandidates()
      case Latest.Stable      => latestStableCandidates()
    }

  @deprecated("Use candidates0 instead", "2.1.25")
  def candidates(kind: Latest): Iterator[String] =
    candidates0(kind).map(_.asString)

  def latest0(kind: Latest): Option[Version0] = {
    val it = candidates0(kind)
    if (it.hasNext)
      Some(it.next())
    else
      None
  }

  @deprecated("Use latest0 instead", "2.1.25")
  def latest(kind: Latest): Option[String] =
    latest0(kind).map(_.asString)

  def candidatesInInterval(itv: VersionInterval0)
    : Iterator[Version0] = {
    val fromRelease = Some(release0).filter(itv.contains)
    def fromAvailable = available0
      .filter(itv.contains)
      .filter(v => !fromRelease.contains(v))
      .sorted
      .distinct
      .reverseIterator

    fromRelease.iterator ++ fromAvailable
  }

  @deprecated("Use the override accepting coursier.version.VersionInterval instead", "2.1.25")
  def candidatesInInterval(itv: VersionInterval): Iterator[String] =
    candidatesInInterval(
      VersionInterval0(
        itv.from.map(_.repr).map(Version0(_)),
        itv.to.map(_.repr).map(Version0(_)),
        itv.fromIncluded,
        itv.toIncluded
      )
    ).map(_.asString)

  def inInterval(itv: VersionInterval0): Option[Version0] = {
    val it = candidatesInInterval(itv)
    if (it.hasNext)
      Some(it.next())
    else
      None
  }

  @deprecated("Use the override accepting coursier.version.VersionInterval instead", "2.1.25")
  def inInterval(itv: VersionInterval): Option[String] =
    inInterval(
      VersionInterval0(
        itv.from.map(_.repr).map(Version0(_)),
        itv.to.map(_.repr).map(Version0(_)),
        itv.fromIncluded,
        itv.toIncluded
      )
    ).map(_.asString)
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

  val empty = Versions(Version0.zero, Version0.zero, Nil, None)

  @deprecated("Use the override accepting Version-s instead", "2.1.25")
  def apply(
    latest: String,
    release: String,
    available: List[String],
    lastUpdated: Option[Versions.DateTime]
  ): Versions =
    apply(
      Version0(latest),
      Version0(release),
      available.map(Version0(_)),
      lastUpdated
    )
}
