package coursier.core

final case class Versions(
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

}

object Versions {
  final case class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  )

  val empty = Versions("", "", Nil, None)
}
