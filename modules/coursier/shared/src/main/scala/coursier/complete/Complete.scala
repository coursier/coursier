package coursier.complete

import coursier.Resolve
import coursier.cache.Cache
import coursier.core.Repository
import coursier.util.Sync

final class Complete[F[_]] private (
  val repositories: Seq[Repository],
  val scalaVersion: Option[String],
  val scalaBinaryVersion: Option[String],
  val input: String,
  val cache: Cache[F],
  val F: Sync[F]
) {

  private implicit def F0: Sync[F] = F

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Complete[F] =>
        repositories == other.repositories &&
          scalaVersion == other.scalaVersion &&
          scalaBinaryVersion == other.scalaBinaryVersion &&
          input == other.input &&
          cache == other.cache &&
          F == other.F
      case _ =>
        false
    }

  override def hashCode(): Int =
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.complete.Complete".##) + repositories.##) + scalaVersion.##) + scalaBinaryVersion.##) + input.##) + cache.##) + F.##

  override def toString: String =
    s"Complete($repositories, $scalaVersion, $scalaBinaryVersion, $input, $cache, $F)"

  private def copy(
    repositories: Seq[Repository] = repositories,
    scalaVersion: Option[String] = scalaVersion,
    scalaBinaryVersion: Option[String] = scalaBinaryVersion,
    input: String = input,
    cache: Cache[F] = cache,
    F: Sync[F] = F
  ): Complete[F] =
    new Complete(repositories, scalaVersion, scalaBinaryVersion, input, cache, F)


  def withRepositories(repositories: Seq[Repository]): Complete[F] =
    copy(repositories = repositories)
  def addRepositories(repository: Repository*): Complete[F] =
    copy(repositories = repositories ++ repository)

  def withScalaVersion(version: String, adjustBinaryVersion: Boolean): Complete[F] =
    copy(
      scalaVersion = Some(version),
      scalaBinaryVersion = if (adjustBinaryVersion) Some(Complete.scalaBinaryVersion(version)) else scalaBinaryVersion
    )
  def withScalaVersion(versionOpt: Option[String], adjustBinaryVersion: Boolean): Complete[F] =
    copy(
      scalaVersion = versionOpt,
      scalaBinaryVersion = if (adjustBinaryVersion) versionOpt.map(Complete.scalaBinaryVersion) else scalaBinaryVersion
    )
  def withScalaVersion(version: String): Complete[F] =
    withScalaVersion(version, adjustBinaryVersion = true)
  def withScalaVersion(versionOpt: Option[String]): Complete[F] =
    withScalaVersion(versionOpt, adjustBinaryVersion = true)
  def withScalaBinaryVersion(version: String): Complete[F] =
    copy(scalaBinaryVersion = Some(version))
  def withScalaBinaryVersion(versionOpt: Option[String]): Complete[F] =
    copy(scalaBinaryVersion = versionOpt)

  def withInput(input: String): Complete[F] =
    copy(input = input)

  def complete(): F[(Int, Seq[String])] =
    F.map(result())(r => (r.from, r.completions))

  def result(): F[Complete.Result] = {

    val completers: Seq[(Repository, Repository.Complete[F])] =
      repositories.flatMap(r => r.completeOpt(cache.fetch).map((r, _)).toSeq)

    val inputF = F.fromAttempt(
      Repository.Complete.parse(input, scalaVersion.getOrElse(""), scalaBinaryVersion.getOrElse(""))
    )

    val t = F.bind(inputF) { input0 =>
      F.map(F.gather(
        completers.map {
          case (repo, c) =>
            F.map(c.complete(input0))(e => repo -> e.right.map(_.completions))
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

  def scalaBinaryVersion(scalaVersion: String): String =
    if (scalaVersion.contains("-M") || scalaVersion.contains("-RC"))
      scalaVersion
    else
      scalaVersion.split('.').take(2).mkString(".")

  final case class Result(
    input: Repository.Complete.Input,
    results: Seq[(Repository, Either[Throwable, Seq[String]])]
  ) {
    def from: Int = input.from
    def completions: Seq[String] =
      results.flatMap(_._2.right.toSeq.flatten)
  }

  def apply[F[_]](cache: Cache[F])(implicit F: Sync[F]): Complete[F] =
    new Complete(Resolve.defaultRepositories, None, None, "", cache, F)
}
