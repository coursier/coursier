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

final class Artifacts[F[_]] private[coursier] (private val params: Artifacts.Params[F]) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Artifacts[_] =>
        params == other.params
    }

  override def hashCode(): Int =
    17 + params.##

  override def toString: String =
    s"Artifacts($params)"

  private def withParams(params: Artifacts.Params[F]): Artifacts[F] =
    new Artifacts(params)


  def withResolution(resolution: Resolution): Artifacts[F] =
    withParams(params.copy(resolution = resolution))
  def withClassifiers(classifiers: Set[Classifier]): Artifacts[F] =
    withParams(params.copy(classifiers = classifiers))
  def withMainArtifacts(mainArtifacts: JBoolean): Artifacts[F] =
    withParams(params.copy(mainArtifacts = mainArtifacts))
  def withArtifactTypes(artifactTypes: Set[Type]): Artifacts[F] =
    withParams(params.copy(artifactTypes = artifactTypes))
  def withCache(cache: Cache[F]): Artifacts[F] =
    withParams(params.copy(cache = cache))

  private def S = params.S

  def io: F[Seq[(Artifact, File)]] = {

    val a = Artifacts.artifacts0(
      params.resolution,
      params.classifiers,
      params.mainArtifacts,
      params.artifactTypes
    )

    Artifacts.fetchArtifacts(
      a.map(_._3),
      params.cache
    )(S)
  }

}

object Artifacts {

  // see Resolve.apply for why cache is passed here
  def apply[F[_]](cache: Cache[F] = Cache.default)(implicit S: Schedulable[F]): Artifacts[F] =
    new Artifacts(
      Params(
        Resolution(),
        Set(),
        null,
        null,
        cache,
        S
      )
    )

  implicit class ArtifactsTaskOps(private val artifacts: Artifacts[Task]) extends AnyVal {

    def future()(implicit ec: ExecutionContext = artifacts.params.cache.ec): Future[Seq[(Artifact, File)]] =
      artifacts.io.future()

    def either()(implicit ec: ExecutionContext = artifacts.params.cache.ec): Either[FetchError, Seq[(Artifact, File)]] = {

      val f = artifacts
        .io
        .map(Right(_))
        .handle { case ex: FetchError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = artifacts.params.cache.ec): Seq[(Artifact, File)] = {
      val f = artifacts.io.future()
      Await.result(f, Duration.Inf)
    }

  }

  private[coursier] final case class Params[F[_]](
    resolution: Resolution,
    classifiers: Set[Classifier],
    mainArtifacts: JBoolean,
    artifactTypes: Set[Type],
    cache: Cache[F],
    S: Schedulable[F]
  )

  def defaultTypes(
    classifiers: Set[Classifier] = Set.empty,
    mainArtifacts: JBoolean = null
  ): Set[Type] = {

    val mainArtifacts0: Boolean =
      if (mainArtifacts == null)
        classifiers.isEmpty
      else
        mainArtifacts

    val fromMainArtifacts =
      if (mainArtifacts0)
        Set[Type](Type.jar, Type.testJar, Type.bundle)
      else
        Set.empty[Type]

    val fromClassifiers = classifiers.flatMap {
      case Classifier.sources => Set(Type.source)
      case Classifier.javadoc => Set(Type.doc)
      case _ => Set.empty[Type]
    }

    fromMainArtifacts ++ fromClassifiers
  }


  private[coursier] def artifacts0(
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

    val artifactTypes0 =
      Option(artifactTypes)
        .getOrElse(defaultTypes(classifiers, mainArtifacts))

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

    if (artifactTypes0(Type.all))
      artifacts
    else
      artifacts.filter {
        case (_, attr, _) =>
          artifactTypes0(attr.`type`)
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
        S.bind(S.delay(logger.init(sizeHint = Some(artifacts.length)))) { _ =>
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

}
