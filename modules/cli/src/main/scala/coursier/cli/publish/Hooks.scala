package coursier.cli.publish

import java.io.PrintStream
import java.util.concurrent.ScheduledExecutorService

import coursier.maven.MavenRepository
import coursier.publish.bintray.BintrayApi
import coursier.publish.fileset.FileSet
import coursier.publish.sonatype.SonatypeApi
import coursier.publish.sonatype.logger.{BatchSonatypeLogger, InteractiveSonatypeLogger}
import coursier.util.Task

import scala.concurrent.duration.DurationInt

trait Hooks {

  type T >: Null

  def beforeUpload(fileSet: FileSet, isSnapshot: Boolean): Task[T] =
    Task.point(null)
  def repository(t: T, repo: PublishRepository, isSnapshot: Boolean): Option[MavenRepository] =
    None
  def afterUpload(t: T): Task[Unit] =
    Task.point(())

}

object Hooks {

  private final class Sonatype(
    repo: PublishRepository.Sonatype,
    api: SonatypeApi,
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

    type T = Option[(SonatypeApi.Profile, String)]

    override def beforeUpload(fileSet0: FileSet, isSnapshot: Boolean): Task[T] =
      if (isSnapshot)
        Task.point(None)
      else
        for {

          p <- PublishTasks.sonatypeProfile(fileSet0, api, logger)

          _ = {
            if (verbosity >= 2)
              out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id}, uri: ${p.uri})")
            else if (verbosity >= 1)
              out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id})")
            else if (verbosity >= 0)
              out.println(s"Selected Sonatype profile ${p.name}")
          }

          id <- api.createStagingRepository(p, "create staging repository")

        } yield Some((p, id))

    override def repository(t: T, repo0: PublishRepository, isSnapshot: Boolean): Option[MavenRepository] =
      t.map {
        case (_, repoId) =>
          repo.releaseRepoOf(repoId)
      }

    override def afterUpload(profileRepoIdOpt: T): Task[Unit] =
      profileRepoIdOpt match {
        case None => Task.point(())
        case Some((profile, repoId)) =>
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

  private final class Bintray(
    api: BintrayApi,
    subject: String,
    repo: String,
    package0: String,
    licenses: Seq[String],
    vcsUrl: String
  ) extends Hooks {

    type T = Object

    override def beforeUpload(fileSet: FileSet, isSnapshot: Boolean): Task[Object] =
      for {
        _ <- api.createRepositoryIfNeeded(subject, repo)
        _ <- api.createPackageIfNeeded(subject, repo, package0, licenses, vcsUrl)
      } yield null

  }

  def dummy: Hooks =
    new Hooks {
      type T = Object
    }

  def sonatype(
    repo: PublishRepository.Sonatype,
    api: SonatypeApi,
    out: PrintStream,
    verbosity: Int,
    batch: Boolean,
    es: ScheduledExecutorService
  ): Hooks =
    new Sonatype(repo, api, out, verbosity, batch, es)

  def bintray(
    api: BintrayApi,
    subject: String,
    repo: String,
    package0: String,
    licenses: Seq[String],
    vcsUrl: String
  ): Hooks =
    new Bintray(api, subject, repo, package0, licenses, vcsUrl)

}
