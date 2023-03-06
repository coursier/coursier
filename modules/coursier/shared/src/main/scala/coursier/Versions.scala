package coursier

import coursier.cache.Cache
import coursier.core.Version
import coursier.error.CoursierError
import coursier.params.{Mirror, MirrorConfFile}
import coursier.util.{Sync, Task}
import coursier.util.Monad.ops._
import dataclass._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@data class Versions[F[_]](
  cache: Cache[F],
  moduleOpt: Option[Module] = None,
  repositories: Seq[Repository] = Resolve.defaultRepositories,
  @since
  mirrorConfFiles: Seq[MirrorConfFile] = Resolve.defaultMirrorConfFiles,
  mirrors: Seq[Mirror] = Nil
)(implicit
  sync: Sync[F]
) {

  private def F = sync

  def addRepositories(repository: Repository*): Versions[F] =
    withRepositories(repositories ++ repository)

  def noMirrors: Versions[F] =
    withMirrors(Nil).withMirrorConfFiles(Nil)

  def addMirrors(mirrors: Mirror*): Versions[F] =
    withMirrors(this.mirrors ++ mirrors)

  def addMirrorConfFiles(mirrorConfFiles: MirrorConfFile*): Versions[F] =
    withMirrorConfFiles(this.mirrorConfFiles ++ mirrorConfFiles)

  def withModule(module: Module): Versions[F] =
    withModuleOpt(Some(module))

  def versions(): F[coursier.core.Versions] =
    result().map(_.versions)

  def finalRepositories: F[Seq[Repository]] =
    allMirrors.map(Mirror.replace(repositories, _))

  private def allMirrors0 =
    mirrors ++ mirrorConfFiles.flatMap(_.mirrors())

  def allMirrors: F[Seq[Mirror]] =
    F.delay(allMirrors0)

  def result(): F[Versions.Result] = {

    val t =
      finalRepositories.flatMap { repositories =>
        F.gather(
          for {
            mod  <- moduleOpt.toSeq
            repo <- repositories
          } yield repo.versions(mod, cache.fetch).run.map(repo -> _.map(_._1))
        )
      }

    val t0 = cache.loggerOpt.fold(t) { logger =>
      logger.using(t)
    }

    t0.map { l =>
      Versions.Result(l)
    }
  }
}

object Versions {

  def apply(): Versions[Task] =
    Versions(Cache.default)

  private def merge(versions: Vector[coursier.core.Versions]): coursier.core.Versions =
    if (versions.isEmpty)
      coursier.core.Versions("", "", Nil, None)
    else if (versions.lengthCompare(1) == 0)
      versions.head
    else {
      val latest  = versions.map(v => Version(v.latest)).max.repr
      val release = versions.map(v => Version(v.release)).max.repr

      val available = versions
        .flatMap(_.available)
        .distinct
        .map(Version(_))
        .sorted
        .map(_.repr)
        .toList

      val lastUpdated = versions
        .flatMap(_.lastUpdated.toSeq)
        .sorted
        .lastOption

      coursier.core.Versions(latest, release, available, lastUpdated)
    }

  @data class Result(
    results: Seq[(Repository, Either[String, coursier.core.Versions])]
  ) {
    def versions: coursier.core.Versions =
      merge(results.flatMap(_._2.toSeq).toVector)
  }
  implicit class VersionsTaskOps(private val versions: Versions[Task]) extends AnyVal {

    def futureResult()(implicit ec: ExecutionContext = versions.cache.ec): Future[Result] =
      versions.result.future()

    def future()(implicit
      ec: ExecutionContext = versions.cache.ec
    ): Future[coursier.core.Versions] =
      versions.versions.future()

    def eitherResult()(implicit
      ec: ExecutionContext = versions.cache.ec
    ): Either[CoursierError, Result] = {

      val f = versions
        .result
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def either()(implicit
      ec: ExecutionContext = versions.cache.ec
    ): Either[CoursierError, coursier.core.Versions] = {

      val f = versions
        .versions
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def runResult()(implicit ec: ExecutionContext = versions.cache.ec): Result = {
      val f = versions.result.future()
      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = versions.cache.ec): coursier.core.Versions = {
      val f = versions.versions.future()
      Await.result(f, Duration.Inf)
    }

  }
}
