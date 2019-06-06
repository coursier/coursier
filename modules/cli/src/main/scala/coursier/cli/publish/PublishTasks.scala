package coursier.cli.publish

import java.time.Instant

import coursier.maven.MavenRepository
import coursier.publish.download.Download
import coursier.publish.download.logger.DownloadLogger
import coursier.publish.fileset.{FileSet, Group}
import coursier.publish.sonatype.SonatypeApi
import coursier.publish.sonatype.logger.SonatypeLogger
import coursier.util.Task

object PublishTasks {

  def updateMavenMetadata(
    fs: FileSet,
    now: Instant,
    download: Download,
    repository: MavenRepository,
    logger: DownloadLogger,
    withMavenSnapshotVersioning: Boolean
  ): Task[FileSet] = {

    val groups = Group.split(fs)

    for {
      groups0 <- Group.addOrUpdateMavenMetadata(groups, now)
      fromRepo <- Group.downloadMavenMetadata(groups.collect { case m: Group.Module => (m.organization, m.name) }, download, repository, logger)
      metadata <- Group.mergeMavenMetadata(fromRepo ++ groups0.collect { case m: Group.MavenMetadata => m }, now)
      groups1 = groups0.flatMap {
        case _: Group.MavenMetadata => Nil
        case m => Seq(m)
      } ++ metadata
      groups2 <- {
        Task.gather.gather {
          groups1.map {
            case m: Group.Module if m.version.endsWith("SNAPSHOT") && !m.version.contains("+") =>
              if (withMavenSnapshotVersioning)
                Group.downloadSnapshotVersioningMetadata(m, download, repository, logger).flatMap { m0 =>
                  m0.addSnapshotVersioning(now, Set("md5", "sha1", "asc")) // meh second arg
                }
              else
                Task.point(m.clearSnapshotVersioning)
            case other =>
              Task.point(other)
          }
        }
      }
      res <- Task.fromEither(Group.merge(groups2).left.map(msg => new Exception(msg)))
    } yield res
  }

  def clearMavenMetadata(fs: FileSet): FileSet = {

    val groups = Group.split(fs)

    val updatedGroups = groups.flatMap {
      case _: Group.MavenMetadata => Nil
      case other => Seq(other)
    }

    Group.mergeUnsafe(updatedGroups)
  }

  def sonatypeProfile(fs: FileSet, api: SonatypeApi, logger: SonatypeLogger): Task[SonatypeApi.Profile] = {

    val groups = Group.split(fs)
    val orgs = groups.map(_.organization).distinct

    api.listProfiles(logger).flatMap { profiles =>
      val m = orgs.map { org =>
        val validProfiles = profiles.filter(p => org.value == p.name || org.value.startsWith(p.name + "."))
        val profileOpt =
          if (validProfiles.isEmpty)
            None
          else
            Some(validProfiles.minBy(_.name.length))
        org -> profileOpt
      }

      val noProfiles = m.collect {
        case (org, None) => org
      }

      if (noProfiles.isEmpty) {
        val m0 = m.collect {
          case (org, Some(p)) => org -> p
        }

        val grouped = m0.groupBy(_._2)

        if (grouped.size > 1)
          Task.fail(new Exception(s"Cannot publish to several Sonatype profiles at once (${grouped.keys.toVector.map(_.name).sorted})"))
        else {
          assert(grouped.size == 1)
          Task.point(grouped.head._1)
        }
      } else
        Task.fail(new Exception(s"No Sonatype profile found to publish under organization(s) ${noProfiles.map(_.value).sorted.mkString(", ")}"))
    }

  }

}
