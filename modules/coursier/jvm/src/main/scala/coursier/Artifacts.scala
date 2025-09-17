package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{ArtifactError, Cache}
import coursier.core._
import coursier.error.FetchError
import coursier.util.{Artifact, Sync, Task}
import coursier.util.Monad.ops._

import scala.collection.compat._
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
  // format: off
  transformArtifacts:
    Seq[Seq[(Dependency, Publication, Artifact)] =>
      Seq[(Dependency, Publication, Artifact)]] =
    Nil,
  @since
  // format: off
  transformArtifacts0:
    Seq[Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] =>
      Seq[(Dependency, Either[VariantPublication, Publication], Artifact)]] =
    Nil,
  extraArtifactsSeq0: Seq[Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] => Seq[Artifact]] = Nil,
  @since("2.1.25")
  attributes: Seq[VariantSelector.AttributesBased] = Nil
  // format: on
)(implicit
  sync: Sync[F]
) {

  private def S = sync

  private def extraArtifacts
    : Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] => Seq[Artifact] =
    l =>
      extraArtifactsSeq0.flatMap(_(l)) ++ {
        if (extraArtifactsSeq.isEmpty) Nil
        else if (l.exists(_._2.isLeft))
          sys.error(
            "Cannot use deprecated Artifacts.transformArtifacts along with Gradle Module variants"
          )
        else {
          val l0 = l.collect {
            case (dep, Right(pub), art) =>
              (dep, pub, art)
          }
          extraArtifactsSeq.flatMap(_(l0))
        }
      }

  def withResolution(resolution: Resolution): Artifacts[F] =
    withResolutions(Seq(resolution))
  def withMainArtifacts(mainArtifacts: JBoolean): Artifacts[F] =
    withMainArtifactsOpt(Option(mainArtifacts).map(x => x))
  def withArtifactTypes(artifactTypes: Set[Type]): Artifacts[F] =
    withArtifactTypesOpt(Option(artifactTypes))

  def addExtraArtifacts(
    f: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]
  ): Artifacts[F] =
    withExtraArtifactsSeq(extraArtifactsSeq :+ f)
  def noExtraArtifacts(): Artifacts[F] =
    withExtraArtifactsSeq(Nil)
  def withExtraArtifacts(
    l: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]]
  ): Artifacts[F] =
    withExtraArtifactsSeq(l)
  def addTransformArtifacts(
    f: Seq[(Dependency, Publication, Artifact)] => Seq[(Dependency, Publication, Artifact)]
  ): Artifacts[F] =
    withTransformArtifacts(transformArtifacts :+ f)
  def addTransformArtifacts0(
    f: Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] => Seq[(
      Dependency,
      Either[VariantPublication, Publication],
      Artifact
    )]
  ): Artifacts[F] =
    withTransformArtifacts0(transformArtifacts0 :+ f)

  def io: F[Seq[(Artifact, File)]] =
    ioResult.map(_.artifacts)

  def ioResult: F[Artifacts.Result] = {

    val transformArtifactsFunc  = Function.chain(transformArtifacts)
    val transformArtifactsFunc0 = Function.chain(transformArtifacts0)
    val finalTransformArtifactsFunc: Seq[(
      Dependency,
      Either[VariantPublication, Publication],
      Artifact
    )] => Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] = {
      seq =>
        if (transformArtifacts.nonEmpty && seq.exists(_._2.isLeft))
          sys.error(
            "Cannot use deprecated Artifacts.transformArtifacts along with Gradle Module variants"
          )
        val seq0 =
          if (transformArtifacts.isEmpty) seq
          else
            transformArtifactsFunc(
              seq.collect {
                case (dep, Right(pub), art) =>
                  (dep, pub, art)
              }
            ).map {
              case (dep, pub, art) =>
                (dep, Right(pub), art)
            }
        transformArtifactsFunc0(seq0)
    }
    val a = finalTransformArtifactsFunc {
      resolutions
        .flatMap { r =>
          Artifacts.artifacts0(
            r,
            classifiers,
            attributes,
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
      .view
      .mapValues(_.map(_._2).distinct)
      .toMap

    val allArtifacts = (a.map(_._3) ++ extraArtifacts(a)).distinct
    val res          = Artifacts.fetchArtifacts(
      allArtifacts,
      cache,
      otherCaches: _*
    )(S)

    res.map { l =>
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
    fullDetailedArtifacts0: Seq[(
      Dependency,
      Either[VariantPublication, Publication],
      Artifact,
      Option[File]
    )],
    fullExtraArtifacts: Seq[(Artifact, Option[File])]
  ) {

    @deprecated("Use fullDetailedArtifacts0 instead", "2.1.25")
    def fullDetailedArtifacts: Seq[(
      Dependency,
      Publication,
      Artifact,
      Option[File]
    )] =
      fullDetailedArtifacts0.map {
        case (dep, Right(pub), art, fOpt) =>
          (dep, pub, art, fOpt)
        case (_, Left(_), _, _) =>
          sys.error("Deprecated method doesn't support Gradle Module variants")
      }
    @deprecated("Use withFullDetailedArtifacts0 instead", "2.1.25")
    def withFullDetailedArtifacts(artifacts: Seq[(
      Dependency,
      Publication,
      Artifact,
      Option[File]
    )]): Result =
      withFullDetailedArtifacts0(
        artifacts.map {
          case (dep, pub, art, fOpt) =>
            (dep, Right(pub), art, fOpt)
        }
      )

    def artifacts: Seq[(Artifact, File)] =
      fullArtifacts
        .collect {
          case (art, Some(file)) =>
            (art, file)
        }

    def detailedArtifacts0
      : Seq[(Dependency, Either[VariantPublication, Publication], Artifact, File)] =
      fullDetailedArtifacts0
        .collect {
          case (dep, pub, art, Some(file)) =>
            (dep, pub, art, file)
        }
        .distinct

    @deprecated("Use detailedArtifacts0 instead", "2.1.25")
    def detailedArtifacts
      : Seq[(Dependency, Publication, Artifact, File)] =
      detailedArtifacts0.map {
        case (dep, Right(pub), art, fOpt) =>
          (dep, pub, art, fOpt)
        case (_, Left(_), _, _) =>
          sys.error("Deprecated method doesn't support Gradle Module variants")
      }

    def extraArtifacts: Seq[(Artifact, File)] =
      fullExtraArtifacts.collect {
        case (art, Some(file)) =>
          (art, file)
      }

    def files: Seq[File] =
      fullArtifacts
        .flatMap(_._2.toSeq)
        .distinct

    def fullArtifacts: Seq[(Artifact, Option[File])] = {
      val artifacts = fullDetailedArtifacts0.map { case (_, _, a, f) => (a, f) } ++
        fullExtraArtifacts
      artifacts.distinct
    }

    @deprecated("Use withFullDetailedArtifacts instead", "2.0.0-RC6-15")
    def withDetailedArtifacts(
      detailedArtifacts: Seq[(Dependency, Publication, Artifact, File)]
    ): Result =
      withFullDetailedArtifacts0(detailedArtifacts.map { case (dep, pub, art, file) =>
        (dep, Right(pub), art, Some(file))
      })
    @deprecated("Use withFullExtraArtifacts instead", "2.0.0-RC6-15")
    def withExtraArtifacts(extraArtifacts: Seq[(Artifact, File)]): Result =
      withFullExtraArtifacts(extraArtifacts.map { case (art, file) => (art, Some(file)) })
  }

  implicit class ArtifactsTaskOps(private val artifacts: Artifacts[Task]) extends AnyVal {

    def future()(implicit
      ec: ExecutionContext = artifacts.cache.ec
    ): Future[Seq[(Artifact, File)]] =
      artifacts.io.future()

    def either()(implicit
      ec: ExecutionContext = artifacts.cache.ec
    ): Either[FetchError, Seq[(Artifact, File)]] = {

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

    def eitherResult()(implicit
      ec: ExecutionContext = artifacts.cache.ec
    ): Either[FetchError, Result] = {

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
      case _                  => Set.empty[Type]
    }

    fromMainArtifacts ++ fromClassifiers
  }

  @deprecated("Use artifacts0 instead", "2.1.25")
  def artifacts(
    resolution: Resolution,
    classifiers: Set[Classifier],
    mainArtifactsOpt: Option[Boolean],
    artifactTypesOpt: Option[Set[Type]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Publication, Artifact)] =
    artifacts0(
      resolution,
      classifiers,
      Nil,
      mainArtifactsOpt,
      artifactTypesOpt,
      classpathOrder
    ).collect {
      case (dep, Right(pub), art) =>
        (dep, pub, art)
    }

  def artifacts0(
    resolution: Resolution,
    classifiers: Set[Classifier],
    attributes: Seq[VariantSelector.AttributesBased],
    mainArtifactsOpt: Option[Boolean],
    artifactTypesOpt: Option[Set[Type]],
    classpathOrder: Boolean
  ): Seq[(Dependency, Either[VariantPublication, Publication], Artifact)] = {

    val mainArtifacts0 = mainArtifactsOpt.getOrElse {
      classifiers.isEmpty && attributes.isEmpty
    }

    val artifactTypes0 = artifactTypesOpt.getOrElse {
      defaultTypes(classifiers, mainArtifactsOpt)
    }

    val main =
      if (mainArtifacts0)
        resolution.dependencyArtifacts0(None, None, classpathOrder = classpathOrder)
      else
        Nil

    val classifiersArtifacts =
      if (classifiers.isEmpty || attributes.nonEmpty)
        Nil
      else
        resolution.dependencyArtifacts0(Some(classifiers.toSeq), None, classpathOrder)

    val attributesArtifacts =
      if (attributes.isEmpty)
        Nil
      else
        attributes.flatMap { attr =>
          resolution.dependencyArtifacts0(
            Some(classifiers.toSeq).filter(_.nonEmpty),
            Some(attr),
            classpathOrder
          )
        }

    val artifacts = (main ++ classifiersArtifacts ++ attributesArtifacts).map {
      case (dep, pub0 @ Right(pub), artifact) =>
        (dep.withAttributes(dep.attributes.withClassifier(pub.classifier)), pub0, artifact)
      case (dep, pub0, artifact) =>
        (dep, pub0, artifact)
    }

    if (artifactTypes0(Type.all))
      artifacts
    else
      artifacts.filter {
        case (_, Left(_), _) =>
          true // ???
        case (_, Right(pub), _) =>
          artifactTypes0(pub.`type`)
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
      .view
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
  ): F[Seq[(Artifact, Option[File])]] = {

    val groupedArtifacts = groupArtifacts(artifacts)

    def reorder[T](l: Seq[(Artifact, T)]): Seq[(Artifact, T)] = {
      val indices = artifacts.zipWithIndex.toMap
      l.sortBy { case (a, _) => indices.getOrElse(a, -1) } // -1 case should never happen
    }

    val tasks = groupedArtifacts.map { l =>
      val tasks0 = l.map { artifact =>
        val file0 = cache.file(artifact)
        file0.run.map(artifact.->)
      }
      S.gather(tasks0)
    }

    // sequential accumulation (we don't have higher level libraries to ease that here…)
    val gathered =
      tasks.foldLeft(S.point(Seq.empty[(Artifact, Either[ArtifactError, File])])) { (acc, f) =>
        for {
          l  <- acc
          l0 <- f
        } yield l ++ l0
      }

    val loggerOpt = cache.loggerOpt

    val task = loggerOpt match {
      case None         => gathered
      case Some(logger) => logger.using(gathered)
    }

    task.flatMap { results =>

      val ignoredErrors  = new mutable.ListBuffer[(Artifact, ArtifactError)]
      val errors         = new mutable.ListBuffer[(Artifact, ArtifactError)]
      val artifactToFile = new mutable.ListBuffer[(Artifact, File)]

      results.foreach {
        case (artifact, Left(err)) if artifact.optional && (err.notFound || err.forbidden) =>
          ignoredErrors += artifact -> err
        case (artifact, Left(err)) =>
          errors += artifact -> err
        case (artifact, Right(f)) =>
          artifactToFile += artifact -> f
      }

      def result(withIgnoredErrors: Boolean): Seq[(Artifact, Option[File])] = {
        val withFiles = artifactToFile.toList.map { case (a, f) => (a, Some(f)) }
        def noFiles   = ignoredErrors.map { case (a, _) => (a, None) }
        reorder(if (withIgnoredErrors) withFiles ++ noFiles else withFiles)
      }

      if (otherCaches.isEmpty)
        if (errors.isEmpty)
          S.point(result(true))
        else
          S.fromAttempt(Left(new FetchError.DownloadingArtifacts(errors.toList)))
      else if (errors.isEmpty && ignoredErrors.isEmpty)
        S.point(result(false))
      else
        fetchArtifacts(
          errors.map(_._1).toSeq ++ ignoredErrors.map(_._1).toSeq,
          otherCaches.head,
          otherCaches.tail: _*
        ).map { l =>
          reorder(result(false) ++ l)
        }
    }
  }

}
