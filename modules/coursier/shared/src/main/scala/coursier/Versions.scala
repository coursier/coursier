package coursier

import coursier.cache.Cache
import coursier.core.Version
import coursier.params.{Mirror, MirrorConfFile}
import coursier.util.{Sync, Task}
import dataclass.data

@data class Versions[F[_]](
  cache: Cache[F],
  moduleOpt: Option[Module] = None,
  repositories: Seq[Repository] = Resolve.defaultRepositories,
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
    F.map(result())(_.versions)

  def finalRepositories: F[Seq[Repository]] =
    F.map(allMirrors)(Mirror.replace(repositories, _))

  private def allMirrors0 =
    mirrors ++ mirrorConfFiles.flatMap(_.mirrors())

  def allMirrors: F[Seq[Mirror]] =
    F.delay(allMirrors0)

  def result(): F[Versions.Result] = {

    val t =
      F.bind(finalRepositories) { repositories =>
        F.gather(
          for {
            mod <- moduleOpt.toSeq
            repo <- repositories
          } yield {
            F.map(repo.versions(mod, cache.fetch).run)(repo -> _.map(_._1))
          }
        )
      }

    val t0 = cache.loggerOpt.fold(t) { logger =>
      F.bind(F.delay(logger.init())) { _ =>
        F.bind(F.attempt(t)) { e =>
          F.bind(F.delay(logger.stop())) { _ =>
            F.fromAttempt(e)
          }
        }
      }
    }

    F.map(t0) { l =>
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
      val latest = versions.map(v => Version(v.latest)).max.repr
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
}
