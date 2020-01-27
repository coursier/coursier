package coursier.complete

import coursier.Resolve
import coursier.cache.Cache
import coursier.core.Repository
import coursier.util.Sync
import dataclass.data
import coursier.util.Task

@data class Complete[F[_]](
  cache: Cache[F],
  repositories: Seq[Repository] = Resolve.defaultRepositories,
  scalaVersionOpt: Option[String] = None,
  scalaBinaryVersionOpt: Option[String] = None,
  input: String = ""
)(implicit
  sync: Sync[F]
) {

  private def F = sync

  def addRepositories(repository: Repository*): Complete[F] =
    withRepositories(repositories ++ repository)

  def withScalaVersion(version: String, adjustBinaryVersion: Boolean): Complete[F] =
    withScalaVersionOpt(Some(version))
      .withScalaBinaryVersionOpt(if (adjustBinaryVersion) Some(Complete.scalaBinaryVersion(version)) else scalaBinaryVersionOpt)
  def withScalaVersion(version: String): Complete[F] =
    withScalaVersion(version, adjustBinaryVersion = true)
  def withScalaVersionOpt(versionOpt: Option[String], adjustBinaryVersion: Boolean): Complete[F] =
    withScalaVersionOpt(versionOpt)
      .withScalaBinaryVersionOpt(if (adjustBinaryVersion) versionOpt.map(Complete.scalaBinaryVersion) else scalaBinaryVersionOpt)
  // FIXME Manage to recover that back (automatically adjusting scalaBinaryVersionOpt when scalaVersionOpt is set)
  // def withScalaVersionOpt(versionOpt: Option[String]): Complete[F] =
  //   withScalaVersionOpt(versionOpt, adjustBinaryVersion = versionOpt.nonEmpty)
  def withScalaBinaryVersion(version: String): Complete[F] =
    withScalaBinaryVersionOpt(Some(version))

  def complete(): F[(Int, Seq[String])] =
    F.map(result())(r => (r.from, r.completions))

  def result(): F[Complete.Result] = {

    val completers: Seq[(Repository, Repository.Complete[F])] =
      repositories.distinct.flatMap(r => r.completeOpt(cache.fetch).map((r, _)).toSeq)

    val inputF = F.fromAttempt(
      Repository.Complete.parse(input, scalaVersionOpt.getOrElse(""), scalaBinaryVersionOpt.getOrElse(""))
    )

    val t = F.bind(inputF) { input0 =>
      F.map(F.gather(
        completers.map {
          case (repo, c) =>
            F.map(c.complete(input0))(e => repo -> e.map(_.completions))
        }
      ))((input0, _))
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

    F.map(t0) {
      case (input0, l) =>
        Complete.Result(input0, l)
    }
  }

}

object Complete {

  def apply(): Complete[Task] =
    Complete(Cache.default)

  def scalaBinaryVersion(scalaVersion: String): String =
    if (scalaVersion.contains("-M") || scalaVersion.contains("-RC"))
      scalaVersion
    else
      scalaVersion.split('.').take(2).mkString(".")

  @data class Result(
    input: Repository.Complete.Input,
    results: Seq[(Repository, Either[Throwable, Seq[String]])]
  ) {
    def from: Int = input.from
    def completions: Seq[String] =
      results.flatMap(_._2.toSeq.flatten)
  }
}
