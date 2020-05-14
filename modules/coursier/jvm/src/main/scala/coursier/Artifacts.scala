package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{ArtifactError, Cache}
import coursier.core.Publication
import coursier.error.FetchError
import coursier.util.{Artifact, Sync, Task}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import dataclass._

@data class Artifacts[F[_]](
  cache: Cache[F],
  resolutions: Seq[Resolution] = Nil,
  classifiers: Set[Classifier] = Set.empty,
  mainArtifactsOpt: Option[Boolean] = None,
  artifactTypesOpt: Option[Set[Type]] = None,
  otherCaches: Seq[Cache[F]] = Nil,
  extraArtifactsSeq: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]] = Nil,
  classpathOrder: Boolean = true,
  @since
  transformArtifacts: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[(Dependency, Publication, Artifact)]] = Nil,
)(implicit
  sync: Sync[F]
) {

  private def S = sync

  private def extraArtifacts: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact] =
    l => extraArtifactsSeq.flatMap(_(l))

  def withResolution(resolution: Resolution): Artifacts[F] =
    withResolutions(Seq(resolution))
  def withMainArtifacts(mainArtifacts: JBoolean): Artifacts[F] =
    withMainArtifactsOpt(Option(mainArtifacts).map(x => x))
  def withArtifactTypes(artifactTypes: Set[Type]): Artifacts[F] =
    withArtifactTypesOpt(Option(artifactTypes))

  def addExtraArtifacts(f: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]): Artifacts[F] =
    withExtraArtifactsSeq(extraArtifactsSeq :+ f)
  def noExtraArtifacts(): Artifacts[F] =
    withExtraArtifactsSeq(Nil)
  def withExtraArtifacts(l: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]]): Artifacts[F] =
    withExtraArtifactsSeq(l)
  def addTransformArtifacts(f: Seq[(Dependency, Publication, Artifact)] => Seq[(Dependency, Publication, Artifact)]): Artifacts[F] =
    withTransformArtifacts(transformArtifacts :+ f)

  def io: F[Seq[(Artifact, File)]] =
    S.map(ioResult)(_.artifacts)

  def ioResult: F[Artifacts.Result] = {

    val transformArtifacts0 = Function.chain(transformArtifacts)
    val a = transformArtifacts0 {
      resolutions
        .flatMap { r =>
          Artifacts.artifacts0(
            r,
            classifiers,
            mainArtifactsOpt,
            artifactTypesOpt,
            classpathOrder
          )
        }
    }

    val byArtifact = a
      .map {
        case (d, p, a) => (a, (d, p))
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2).distinct)
      .toMap

    val allArtifacts = (a.map(_._3) ++ extraArtifacts(a)).distinct
    val res = Artifacts.fetchArtifacts(
      allArtifacts,
      cache,
      otherCaches: _*
    )(S)

    S.map(res) { l =>
      val l0 = l.map {
        case (a, f) =>
          byArtifact.get(a) match {
            case None =>
              (Nil, Seq((a, f)))
            case Some(depPubs) =>
              val l = depPubs.map {
                case (d, p) => (d, p, a, f)
              }
              (l, Nil)
          }
      }

      Artifacts.Result(l0.flatMap(_._1), l0.flatMap(_._2))
    }
  }

}

object Artifacts {

  def apply(): Artifacts[Task] =
    new Artifacts(Cache.default)


  @data class Result(
    detailedArtifacts: Seq[(Dependency, Publication, Artifact, File)],
    extraArtifacts: Seq[(Artifact, File)]
  ) {

    def artifacts: Seq[(Artifact, File)] = {
      val artifacts = detailedArtifacts.map { case (_, _, a, f) => (a, f) } ++ extraArtifacts
      artifacts.distinct
    }

    def files: Seq[File] =
      artifacts.map(_._2).distinct
  }


  implicit class ArtifactsTaskOps(private val artifacts: Artifacts[Task]) extends AnyVal {

    def future()(implicit ec: ExecutionContext = artifacts.cache.ec): Future[Seq[(Artifact, File)]] =
      artifacts.io.future()

    def either()(implicit ec: ExecutionContext = artifacts.cache.ec): Either[FetchError, Seq[(Artifact, File)]] = {

      val f = artifacts
        .io
        .map(Right(_))
        .handle { case ex: FetchError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = artifacts.cache.ec): Seq[(Artifact, File)] = {
      val f = artifacts.io.future()
      Await.result(f, Duration.Inf)
    }

    def futureResult()(implicit ec: ExecutionContext = artifacts.cache.ec): Future[Result] =
      artifacts.ioResult.future()

    def eitherResult()(implicit ec: ExecutionContext = artifacts.cache.ec): Either[FetchError, Result] = {

      val f = artifacts
        .ioResult
        .map(Right(_))
        .handle { case ex: FetchError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def runResult()(implicit ec: ExecutionContext = artifacts.cache.ec): Result = {
      val f = artifacts.ioResult.future()
      Await.result(f, Duration.Inf)
    }

  }

  def defaultTypes(
    classifiers: Set[Classifier] = Set.empty,
    mainArtifactsOpt: Option[Boolean] = None
  ): Set[Type] = {

    val mainArtifacts0 = mainArtifactsOpt.getOrElse(classifiers.isEmpty)

    val fromMainArtifacts =
      if (mainArtifacts0)
        coursier.core.Resolution.defaultTypes
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
    mainArtifactsOpt: Option[Boolean],
    artifactTypesOpt: Option[Set[Type]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Publication, Artifact)] = {

    val mainArtifacts0 = mainArtifactsOpt.getOrElse(classifiers.isEmpty)

    val artifactTypes0 =
      artifactTypesOpt
        .getOrElse(defaultTypes(classifiers, mainArtifactsOpt))

    val main =
      if (mainArtifacts0)
        resolution.dependencyArtifacts(None, classpathOrder)
      else
        Nil

    val classifiersArtifacts =
      if (classifiers.isEmpty)
        Nil
      else
        resolution.dependencyArtifacts(Some(classifiers.toSeq), classpathOrder)

    val artifacts = (main ++ classifiersArtifacts).map {
      case (dep, pub, artifact) =>
        (dep.withAttributes(dep.attributes.withClassifier(pub.classifier)), pub, artifact)
    }

    if (artifactTypes0(Type.all))
      artifacts
    else
      artifacts.filter {
        case (_, attr, _) =>
          artifactTypes0(attr.`type`)
      }
  }

  // Some artifacts may have the same URL. The progress bar logger rejects downloading the same URL twice in parallel
  // (so that we don't display 2 or more progress bars for a single download).
  // To circumvent that, we group the artifacts so that the URLs are unique in each group, then only
  // download the artifacts of each group in parallel.
  private[coursier] def groupArtifacts(artifacts: Seq[Artifact]): Seq[Seq[Artifact]] =
    artifacts
      .groupBy(_.url)
      .iterator
      .flatMap(_._2.zipWithIndex.iterator)
      .toVector
      .groupBy(_._2)
      .mapValues(_.map(_._1))
      .toVector
      .sortBy(_._1)
      .map(_._2)

  private[coursier] def fetchArtifacts[F[_]](
    artifacts: Seq[Artifact],
    cache: Cache[F],
    otherCaches: Cache[F]*
  )(implicit
     S: Sync[F]
  ): F[Seq[(Artifact, File)]] = {

    val groupedArtifacts = groupArtifacts(artifacts)

    def reorder[T](l: Seq[(Artifact, T)]): Seq[(Artifact, T)] = {
      val indices = artifacts.zipWithIndex.toMap
      l.sortBy { case (a, _) => indices.getOrElse(a, -1) } // -1 case should never happen
    }

    val tasks = groupedArtifacts.map { l =>
      val tasks0 = l.map { artifact =>
        val file0 = cache.file(artifact)
        S.map(file0.run)(artifact.->)
      }
      S.gather(tasks0)
    }

    // sequential accumulation (we don't have higher level libraries to ease that here…)
    val gathered = tasks.foldLeft(S.point(Seq.empty[(Artifact, Either[ArtifactError, File])])) { (acc, f) =>
      // for (l <- acc; l0 <- f) yield l ++ l0
      S.bind(acc) { l =>
        S.map(f) { l0 =>
          l ++ l0
        }
      }
    }

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

      if (otherCaches.isEmpty) {
        if (errors.isEmpty)
          S.point(reorder(artifactToFile.toList))
        else
          S.fromAttempt(Left(new FetchError.DownloadingArtifacts(errors.toList)))
      } else {
        if (errors.isEmpty && ignoredErrors.isEmpty)
          S.point(reorder(artifactToFile.toList))
        else
          S.map(fetchArtifacts(errors.map(_._1).toSeq ++ ignoredErrors.map(_._1).toSeq, otherCaches.head, otherCaches.tail: _*)) { l =>
            reorder(artifactToFile.toList ++ l)
          }
      }
    }
  }

}
