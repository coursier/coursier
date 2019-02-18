package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{ArtifactError, Cache}
import coursier.core.{Classifier, Type}
import coursier.error.FetchError
import coursier.util.{Schedulable, Task}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

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
    cache: Cache[F] = Cache.default
  )(implicit
     S: Schedulable[F]
  ): F[Seq[(Artifact, File)]] = {

    val tasks = artifacts.map { artifact =>
      val file0 = cache.file(artifact)
      S.map(file0.run)(artifact.->)
    }

    val gathered = S.gather(tasks)

    val loggerOpt = cache.loggerOpt

    val task = loggerOpt match {
      case None =>
        gathered
      case Some(logger) =>
        S.bind(S.delay(logger.init())) { _ =>
          S.bind(S.attempt(gathered)) { a =>
            S.bind(S.delay(logger.stop())) { _ =>
              S.fromAttempt(a)
            }
          }
        }
    }

    S.bind(task) { results =>

      val ignoredErrors = new mutable.ListBuffer[(Artifact, ArtifactError)]
      val errors = new mutable.ListBuffer[(Artifact, ArtifactError)]
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
        S.fromAttempt(Left(new FetchError.DownloadingArtifacts(errors.toList)))
    }
  }

  def fetchIO[F[_]](
    resolution: Resolution,
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = core.Resolution.defaultTypes,
    cache: Cache[F] = Cache.default
  )(implicit
     S: Schedulable[F] = Task.schedulable
  ): F[Seq[(Artifact, File)]] = {

    val a = artifacts(
      resolution,
      classifiers,
      mainArtifacts,
      artifactTypes
    )

    fetchArtifacts(
      a.map(_._3),
      cache
    )
  }

  def fetchFuture(
    resolution: Resolution,
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = core.Resolution.defaultTypes,
    cache: Cache[Task] = Cache.default
  )(implicit ec: ExecutionContext = cache.ec): Future[Seq[(Artifact, File)]] = {

    val task = fetchIO(
      resolution,
      classifiers,
      mainArtifacts,
      artifactTypes,
      cache
    )

    task.future()
  }

  def fetchEither(
    resolution: Resolution,
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = core.Resolution.defaultTypes,
    cache: Cache[Task] = Cache.default
  )(implicit ec: ExecutionContext = cache.ec): Either[FetchError, Seq[(Artifact, File)]] = {

    val task = fetchIO(
      resolution,
      classifiers,
      mainArtifacts,
      artifactTypes,
      cache
    )

    val f = task
      .map(Right(_))
      .handle { case ex: FetchError => Left(ex) }
      .future()

    Await.result(f, Duration.Inf)
  }

  def fetch(
    resolution: Resolution,
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = core.Resolution.defaultTypes,
    cache: Cache[Task] = Cache.default
  )(implicit ec: ExecutionContext = cache.ec): Seq[(Artifact, File)] = {

    val task = fetchIO(
      resolution,
      classifiers,
      mainArtifacts,
      artifactTypes,
      cache
    )

    Await.result(task.future(), Duration.Inf)
  }

}
