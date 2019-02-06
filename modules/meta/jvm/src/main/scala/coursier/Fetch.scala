package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{CacheDefaults, CacheLogger}
import coursier.core.{Classifier, Type}
import coursier.util.Schedulable

import scala.collection.mutable

object Fetch {

  private[coursier] def artifacts(
    resolution: Resolution,
    classifiers: Set[Classifier],
    mainArtifacts: JBoolean,
    artifactTypes: Set[Type]
  ): Seq[(Dependency, Attributes, Artifact)] = {

    val mainArtifacts0: Boolean =
      if (mainArtifacts == null)
        classifiers.isEmpty
      else
        mainArtifacts

    val main =
      if (mainArtifacts0)
        resolution.dependencyArtifacts(None)
      else
        Nil

    val classifiersArtifacts =
      if (classifiers.isEmpty)
        Nil
      else
        resolution.dependencyArtifacts(Some(classifiers.toSeq))

    val artifacts = (main ++ classifiersArtifacts).map {
      case (dep, attr, artifact) =>
        (dep.copy(attributes = dep.attributes.copy(classifier = attr.classifier)), attr, artifact)
    }

    if (artifactTypes(Type.all))
      artifacts
    else
      artifacts.filter {
        case (_, attr, _) =>
          artifactTypes(attr.`type`)
      }
  }

  private[coursier] def fetchArtifacts[F[_]](
    artifacts: Seq[Artifact],
    cache: Cache[F] = Cache.default,
    logger: CacheLogger = CacheLogger.nop,
    retryCount: Int = CacheDefaults.defaultRetryCount
  )(
    implicit
    S: Schedulable[F]
  ): F[Seq[(Artifact, File)]] = {

    val tasks = artifacts.map { artifact =>
      val file0 = cache.file(artifact, retry = retryCount)
      S.map(file0.run)(artifact.->)
    }

    val gathered = S.gather(tasks)

    val task = S.bind(S.delay(logger.init())) { _ =>
      S.bind(S.attempt(gathered)) { a =>
        S.bind(S.delay(logger.stopDidPrintSomething())) { _ =>
          S.fromAttempt(a)
        }
      }
    }

    S.bind(task) { results =>
      val ignoredErrors = new mutable.ListBuffer[(Artifact, FileError)]
      val errors = new mutable.ListBuffer[(Artifact, FileError)]
      val artifactToFile = new mutable.ListBuffer[(Artifact, File)]

      results.foreach {
        case (artifact, Left(err)) if artifact.optional && err.notFound =>
          ignoredErrors += artifact -> err
        case (artifact, Left(err)) =>
          errors += artifact -> err
        case (artifact, Right(f)) =>
          artifactToFile += artifact -> f
      }

      if (errors.isEmpty)
        S.point(artifactToFile.toList)
      else
        // FIXME Use more specific exception type
        S.fromAttempt(Left(new DownloadingArtifactException(errors.toList)))
    }
  }

  def fetch[F[_]](
    resolution: Resolution,
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = core.Resolution.defaultTypes,
    cache: Cache[F] = Cache.default,
    logger: CacheLogger = CacheLogger.nop,
    retryCount: Int = CacheDefaults.defaultRetryCount
  )(
    implicit
    S: Schedulable[F]
  ): F[Seq[File]] = {

    val a = artifacts(
      resolution,
      classifiers,
      mainArtifacts,
      artifactTypes
    )

    val r = fetchArtifacts(
      a.map(_._3),
      cache,
      logger,
      retryCount
    )

    S.map(r)(_.map(_._2))
  }

  final class DownloadingArtifactException(val errors: Seq[(Artifact, FileError)])
      extends Exception(
        "Error fetching artifacts:\n" +
          errors.map { case (a, e) => s"${a.url}: ${e.describe}\n" }.mkString
      )

}
