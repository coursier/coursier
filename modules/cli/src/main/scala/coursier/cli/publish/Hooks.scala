package coursier.cli.publish

import java.io.PrintStream
import java.util.concurrent.ScheduledExecutorService

import coursier.maven.MavenRepository
import coursier.publish.fileset.FileSet
import coursier.publish.sonatype.SonatypeApi
import coursier.publish.sonatype.logger.{BatchSonatypeLogger, InteractiveSonatypeLogger}
import coursier.util.Task

import scala.concurrent.duration.DurationInt

trait Hooks {

  type T

  def beforeUpload(fileSet: FileSet, isSnapshot: Boolean): Task[T]
  def repository(t: T, repo: PublishRepository, isSnapshot: Boolean): Option[MavenRepository] =
    None
  def afterUpload(t: T): Task[Unit]

}

object Hooks {

  private final class Sonatype(
    apiOpt: Option[(PublishRepository.Sonatype, SonatypeApi)],
    out: PrintStream,
    verbosity: Int,
    batch: Boolean,
    es: ScheduledExecutorService
  ) extends Hooks {

    val logger =
      if (batch)
        new BatchSonatypeLogger(out, verbosity)
      else
        InteractiveSonatypeLogger.create(out, verbosity)

    type T = Option[(PublishRepository.Sonatype, SonatypeApi, SonatypeApi.Profile, String)]
    def beforeUpload(fileSet0: FileSet, isSnapshot: Boolean): Task[T] =
      for {

        sonatypeApiProfileOpt <- {
          // params.sonatypeApiOpt(out)
          apiOpt match {
            case None =>
              Task.point(None)
            case Some((repo, api)) =>
              PublishTasks.sonatypeProfile(fileSet0, api, logger)
                .map(p => Some((repo, api, p)))
          }
        }
        _ = {
          for ((_, _, p) <- sonatypeApiProfileOpt)
            if (verbosity >= 2)
              out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id}, uri: ${p.uri})")
            else if (verbosity >= 1)
              out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id})")
            else if (verbosity >= 0)
              out.println(s"Selected Sonatype profile ${p.name}")
        }

        sonatypeApiProfileRepoIdOpt <- sonatypeApiProfileOpt match {
          case Some((repo, api, p)) if !isSnapshot =>
            api
              .createStagingRepository(p, "create staging repository")
              .map(id => Some((repo, api, p, id)))
          case _ =>
            Task.point(None)
        }

      } yield sonatypeApiProfileRepoIdOpt

    override def repository(t: T, repo: PublishRepository, isSnapshot: Boolean): Option[MavenRepository] =
      t match {
        case Some((sonatypeRepo, _, _, repoId)) if !isSnapshot =>
          Some(sonatypeRepo.releaseRepoOf(repoId))
        case _ =>
          None
      }

    def afterUpload(sonatypeApiProfileRepoIdOpt: T): Task[Unit] =
      sonatypeApiProfileRepoIdOpt match {
        case None => Task.point(())
        case Some((_, api, profile, repoId)) =>
          // TODO Print sensible error messages if anything goes wrong here (commands to finish promoting, etc.)
          for {
            _ <- api.sendCloseStagingRepositoryRequest(profile, repoId, "closing repository")
            _ <- api.waitForStatus(profile.id, repoId, "closed", 20, 3.seconds, 1.5, es)
            _ <- api.sendPromoteStagingRepositoryRequest(profile, repoId, "promoting repository")
            _ <- api.waitForStatus(profile.id, repoId, "released", 20, 3.seconds, 1.5, es)
            _ <- api.sendDropStagingRepositoryRequest(profile, repoId, "dropping repository")
          } yield ()
      }
    }

  def sonatype(
    apiOpt: Option[(PublishRepository.Sonatype, SonatypeApi)],
    out: PrintStream,
    verbosity: Int,
    batch: Boolean,
    es: ScheduledExecutorService
  ): Hooks =
    new Sonatype(apiOpt, out, verbosity, batch, es)

}
