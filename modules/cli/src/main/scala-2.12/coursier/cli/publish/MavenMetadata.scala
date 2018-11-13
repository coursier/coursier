package coursier.cli.publish

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale

import coursier.core.{ModuleName, Organization}

import scala.util.Try
import scala.xml.{Elem, Node}

object MavenMetadata {

  final case class Info(
    latest: Option[String],
    release: Option[String],
    versions: Seq[String],
    lastUpdated: Option[LocalDateTime]
  )

  private val lastUpdatedPattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  val timestampPattern = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")

  def create(
    org: Organization,
    name: ModuleName,
    latest: Option[String],
    release: Option[String],
    versions: Seq[String],
    lastUpdated: LocalDateTime
  ): Elem =
    <metadata modelVersion="1.1.0">
      <groupId>{org.value}</groupId>
      <artifactId>{name.value}</artifactId>
      <versioning>
        {latest.fold(<latest/>)(v => <latest>{v}</latest>)}
        {release.fold(<release/>)(v => <release>{v}</release>)}
        <versions>{versions.map(v => <version>{v}</version>)}</versions>
        <lastUpdated>{lastUpdated.format(lastUpdatedPattern)}</lastUpdated>
      </versioning>
    </metadata>

  def create(
    org: Organization,
    name: ModuleName,
    latest: Option[String],
    release: Option[String],
    versions: Seq[String],
    lastUpdated: Instant
  ): Elem =
    create(
      org,
      name,
      latest,
      release,
      versions,
      lastUpdated.atOffset(ZoneOffset.UTC).toLocalDateTime
    )

  def isReleaseVersion(version: String): Boolean =
    // kind of flaky…
    version
      .toLowerCase(Locale.ROOT)
      .replaceAllLiterally("final", "")
      .forall(c => c.isDigit || c == '.' || c == '-')

  def update(
    content: Elem,
    setOrg: Option[Organization],
    setName: Option[ModuleName],
    setLatest: Option[String],
    setRelease: Option[String],
    addVersions: Seq[String],
    setLastUpdated: Option[LocalDateTime]
  ): Elem =
    // FIXME Only updates previous tags for now, doesn't add those that need to be added
    content.copy(
      child = content.child.map {
        case n if n.label == "groupId" =>
          setOrg.fold(n)(g => <groupId>{g.value}</groupId>)
        case n if n.label == "artifactId" =>
          setName.fold(n)(name => <artifactId>{name.value}</artifactId>)
        case n: Elem if n.label == "versioning" =>
          n.copy(
            child = n.child.map {
              case n if n.label == "latest" =>
                setLatest.fold(n)(v => <latest>{v}</latest>)
              case n if n.label == "release" =>
                setRelease.fold(n)(v => <release>{v}</release>)
              case n: Elem if n.label == "versions" =>
                val found = n
                  .child
                  .collect {
                    case n if n.label == "version" => n.text
                  }
                  .toSet
                val toAdd = addVersions.filter(!found(_))
                n.copy(
                  child = n.child ++ toAdd.map { v =>
                    <version>{v}</version>
                  }
                )
              case n if n.label == "lastUpdated" =>
                setLastUpdated.fold(n)(t => <lastUpdated>{t.format(lastUpdatedPattern)}</lastUpdated>)
              case n => n
            }
          )
        case n => n
      }
    )

  def createSnapshotVersioning(
    org: Organization,
    name: ModuleName,
    version: String,
    snapshotVersioning: (LocalDateTime, Int),
    now: Instant,
    artifacts: Seq[(Option[String], String, String, LocalDateTime)]
  ) =
    <metadata modelVersion="1.1.0">
      <groupId>{org.value}</groupId>
      <artifactId>{name.value}</artifactId>
      <version>{version}</version>
      <versioning>
        <snapshot>
          <timestamp>{snapshotVersioning._1.format(timestampPattern)}</timestamp>
          <buildNumber>{snapshotVersioning._2}</buildNumber>
        </snapshot>
        <lastUpdated>{now.atOffset(ZoneOffset.UTC).toLocalDateTime.format(lastUpdatedPattern)}</lastUpdated>
        <snapshotVersions>
          {
            artifacts.map {
              case (classifierOpt, ext, value, updated) =>
                <snapshotVersion>
                  { classifierOpt.fold[Seq[Node]](Nil)(c => Seq(<classifier>{c}</classifier>)) }
                  <extension>{ext}</extension>
                  <value>{value}</value>
                  <updated>{updated.format(lastUpdatedPattern)}</updated>
                </snapshotVersion>
            }
          }
        </snapshotVersions>
      </versioning>
    </metadata>

  def snapshotVersioningBuildNumber(elem: Elem) =
    elem.child.collectFirst {
      case n: Elem if n.label == "versioning" =>
        n.child.collectFirst {
          case n if n.label == "snapshot" =>
            n.child.collectFirst {
              case n if n.label == "buildNumber" =>
                Try(n.text.toInt).toOption
            }.flatten
        }.flatten
    }.flatten

  def currentSnapshotVersioning(elem: Elem) =
    elem.child.collectFirst {
      case n: Elem if n.label == "versioning" =>
        n.child.collectFirst {
          case n if n.label == "snapshot" =>
            val buildNumOpt = n.child.collectFirst {
              case n if n.label == "buildNumber" =>
                Try(n.text.toInt).toOption
            }.flatten
            val timestampOpt = n.child.collectFirst {
              case n if n.label == "timestamp" =>
                Try(LocalDateTime.parse(n.text, timestampPattern)).toOption
            }.flatten

            (buildNumOpt, timestampOpt) match {
              case (Some(n), Some(ts)) => Some((n, ts))
              case (None, None) => None
              case _ => ??? // Report via return type
            }
        }.flatten
    }.flatten

  def updateSnapshotVersioning(
    content: Elem,
    setOrg: Option[Organization],
    setName: Option[ModuleName],
    setVersion: Option[String],
    setSnapshotVersioning: Option[(LocalDateTime, Int)],
    setLastUpdated: Option[LocalDateTime],
    addArtifacts: Seq[(Option[String], String, String, LocalDateTime)]
  ): Elem =
    // FIXME Only updates previous tags for now, doesn't add those that need to be added
    content.copy(
      child = content.child.map {
        case n if n.label == "groupId" =>
          setOrg.fold(n)(g => <groupId>{g.value}</groupId>)
        case n if n.label == "artifactId" =>
          setName.fold(n)(name => <artifactId>{name.value}</artifactId>)
        case n if n.label == "version" =>
          setVersion.fold(n)(v => <version>{v}</version>)
        case n: Elem if n.label == "versioning" =>
          n.copy(
            child = n.child.map {
              case n if n.label == "snapshot" =>
                setSnapshotVersioning.fold(n) {
                  case (dt, num) =>
                    <snapshot>
                      <timestamp>{dt.format(timestampPattern)}</timestamp>
                      <buildNumber>{num}</buildNumber>
                    </snapshot>
                }
              case n: Elem if n.label == "snapshotVersions" =>

                val m = addArtifacts.map {
                  case (c, ext, ver, lm) =>
                    (c, ext) -> (ver, lm)
                }.toMap

                val keep = n.child.flatMap {
                  case n if n.label == "snapshotVersion" =>
                    val classifierOpt = n.child.collectFirst {
                      case n0 if n0.label == "classifier" => n0.text
                    }
                    val extension = n.child.collectFirst {
                      case n0 if n0.label == "extension" => n0.text
                    }.getOrElse("")
                    if (m.contains((classifierOpt, extension)))
                      Nil
                    else
                      Seq(n)
                  case n => Seq(n)
                }

                <snapshotVersions>
                  {
                    addArtifacts.map {
                      case (c, ext, ver, lm) =>
                        <snapshotVersion>
                          {c.fold(Seq.empty[Elem])(c0 => Seq(<classifier>{c0}</classifier>))}
                          <extension>{ext}</extension>
                          <value>{ver}</value>
                          <updated>{lm.format(lastUpdatedPattern)}</updated>
                        </snapshotVersion>
                    } ++ keep
                  }
                </snapshotVersions>

              case n if n.label == "lastUpdated" =>
                setLastUpdated.fold(n)(t => <lastUpdated>{t.format(lastUpdatedPattern)}</lastUpdated>)
              case n => n
            }
          )
        case n => n
      }
    )

  def info(elem: Elem): Info = {

    val latestOpt = elem
      .child
      .collectFirst {
        case n: Elem if n.label == "versioning" =>
          n.child.collectFirst {
            case n0 if n0.label == "latest" =>
              n0.text
          }
      }
      .flatten

    val releaseOpt = elem
      .child
      .collectFirst {
        case n: Elem if n.label == "versioning" =>
          n.child.collectFirst {
            case n0 if n0.label == "release" =>
              n0.text
          }
      }
      .flatten

    val versions = elem
      .child
      .collectFirst {
        case n: Elem if n.label == "versioning" =>
          n.child.collectFirst {
            case n0: Elem if n0.label == "versions" =>
              n0.child.collect {
                case v if v.label == "version" => v.text
              }
          }
      }
      .flatten
      .toSeq
      .flatten

    val lastUpdatedOpt = elem
      .child
      .collectFirst {
        case n: Elem if n.label == "versioning" =>
          n.child.collectFirst {
            case n0 if n0.label == "lastUpdated" =>
              // format error throw here…
              LocalDateTime.from(lastUpdatedPattern.parse(n0.text))
          }
      }
      .flatten

    Info(
      latestOpt,
      releaseOpt,
      versions,
      lastUpdatedOpt
    )
  }

  def print(elem: Elem): String =
    Pom.print(elem)

}
