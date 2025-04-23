package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{Cache, FileCache}
import coursier.core.{
  BomDependency,
  Classifier,
  Dependency,
  Publication,
  Repository,
  Resolution,
  ResolutionProcess,
  Type,
  VariantPublication,
  VariantSelector
}
import coursier.error.CoursierError
import coursier.internal.FetchCache
import coursier.params.{Mirror, ResolutionParams}
import coursier.util.{Artifact, Sync, Task}
import coursier.util.Monad.ops._
import dataclass.data

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@data class Fetch[F[_]](
  private val resolve: Resolve[F],
  private val artifacts: Artifacts[F],
  fetchCacheOpt: Option[File]
) {

  def dependencies: Seq[Dependency] =
    resolve.dependencies
  def repositories: Seq[Repository] =
    resolve.repositories
  def mirrors: Seq[Mirror] =
    resolve.mirrors
  def resolutionParams: ResolutionParams =
    resolve.resolutionParams
  def cache: Cache[F] =
    resolve.cache
  def throughOpt: Option[F[Resolution] => F[Resolution]] =
    resolve.throughOpt
  def transformFetcherOpt: Option[ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]] =
    resolve.transformFetcherOpt
  def sync: Sync[F] =
    resolve.sync

  def classifiers: Set[Classifier] =
    artifacts.classifiers
  def mainArtifactsOpt: Option[Boolean] =
    artifacts.mainArtifactsOpt
  def artifactTypesOpt: Option[Set[Type]] =
    artifacts.artifactTypesOpt
  def extraArtifactsSeq: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]] =
    artifacts.extraArtifactsSeq
  def transformArtifacts
    : Seq[Seq[(Dependency, Publication, Artifact)] => Seq[(Dependency, Publication, Artifact)]] =
    artifacts.transformArtifacts

  def classpathOrder: Boolean =
    artifacts.classpathOrder

  private implicit def S: Sync[F] = resolve.sync

  private def cacheKeyOpt: Option[FetchCache.Key] = {

    val mayBeCached =
      resolve.throughOpt.isEmpty &&
      resolve.transformFetcherOpt.isEmpty &&
      artifacts.extraArtifactsSeq.isEmpty &&
      artifacts.resolutions.isEmpty

    if (mayBeCached)
      artifacts.cache match {
        case f: FileCache[F] =>
          val key = FetchCache.Key(
            resolve.finalDependencies,
            resolve.repositories,
            resolve
              .resolutionParams
              // taken into account in resolve.finalDependencies
              .withExclusions(Set())
              // these are taken into account below
              .withForceVersion0(Map())
              .withProperties(Nil)
              .withForcedProperties(Map())
              .withProfiles(Set()),
            resolve.resolutionParams
              .forceVersion0
              .toVector
              .map {
                case (k, v) =>
                  (k, v.asString)
              }
              .sortBy {
                case (m, v) =>
                  s"$m:$v"
              },
            resolve.resolutionParams.properties.toVector.sortBy { case (k, v) => s"$k=$v" },
            resolve.resolutionParams.forcedProperties.toVector.sortBy { case (k, v) => s"$k=$v" },
            resolve.resolutionParams.profiles.toVector.sorted,
            f.location.getAbsolutePath,
            artifacts.classifiers.toVector.sorted,
            artifacts.mainArtifactsOpt,
            artifacts.artifactTypesOpt.map(_.toVector.sorted)
          )
          Some(key)
        case _ =>
          None
      }
    else
      None
  }

  def canBeCached: Boolean =
    cacheKeyOpt.nonEmpty

  def withDependencies(dependencies: Seq[Dependency]): Fetch[F] =
    withResolve(resolve.withDependencies(dependencies))
  def addDependencies(dependencies: Dependency*): Fetch[F] =
    withResolve(resolve.withDependencies(resolve.dependencies ++ dependencies))

  def withBomDependencies(bomDependencies: Seq[Dependency]): Fetch[F] =
    withResolve(resolve.withBomDependencies(bomDependencies))
  @deprecated("Use addBoms instead", "2.1.18")
  def addBomDependencies(bomDependencies: Dependency*): Fetch[F] =
    withResolve(resolve.withBomDependencies(resolve.bomDependencies ++ bomDependencies))
  def addBoms(bomDependencies: BomDependency*): Fetch[F] =
    withResolve(resolve.withBoms(resolve.boms ++ bomDependencies))

  def withRepositories(repositories: Seq[Repository]): Fetch[F] =
    withResolve(resolve.withRepositories(repositories))
  def addRepositories(repositories: Repository*): Fetch[F] =
    withResolve(resolve.withRepositories(resolve.repositories ++ repositories))

  def noMirrors: Fetch[F] =
    withResolve(resolve.noMirrors)

  def withMirrors(mirrors: Seq[Mirror]): Fetch[F] =
    withResolve(resolve.withMirrors(mirrors))
  def addMirrors(mirrors: Mirror*): Fetch[F] =
    withResolve(resolve.withMirrors(resolve.mirrors ++ mirrors))

  def withResolutionParams(resolutionParams: ResolutionParams): Fetch[F] =
    withResolve(resolve.withResolutionParams(resolutionParams))
  def mapResolutionParams(f: ResolutionParams => ResolutionParams): Fetch[F] =
    withResolve(resolve.withResolutionParams(f(resolutionParams)))

  def withCache(cache: Cache[F]): Fetch[F] =
    withResolve(resolve.withCache(cache))
      .withArtifacts(artifacts.withCache(cache))

  def withResolveCache(cache: Cache[F]): Fetch[F] =
    withResolve(resolve.withCache(cache))
  def withArtifactsCache(cache: Cache[F]): Fetch[F] =
    withArtifacts(artifacts.withCache(cache))

  def withOtherArtifactsCaches(caches: Seq[Cache[F]]): Fetch[F] =
    withArtifacts(artifacts.withOtherCaches(caches))

  def withFetchCache(location: File): Fetch[F] =
    withFetchCacheOpt(Some(location))
  def withFetchCache(locationOpt: Option[File]): Fetch[F] =
    withFetchCacheOpt(locationOpt)

  def transformResolution(f: F[Resolution] => F[Resolution]): Fetch[F] =
    withResolve(resolve.withThroughOpt(Some(resolve.throughOpt.fold(f)(_ andThen f))))
  def noTransformResolution(): Fetch[F] =
    withResolve(resolve.withThroughOpt(None))
  def withTransformResolution(fOpt: Option[F[Resolution] => F[Resolution]]): Fetch[F] =
    withResolve(resolve.withThroughOpt(fOpt))

  def transformFetcher(f: ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]): Fetch[F] =
    withResolve(
      resolve.withTransformFetcherOpt(Some(resolve.transformFetcherOpt.fold(f)(_ andThen f)))
    )
  def noTransformFetcher(): Fetch[F] =
    withResolve(resolve.withTransformFetcherOpt(None))
  def withTransformFetcher(
    fOpt: Option[ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]]
  ): Fetch[F] =
    withResolve(resolve.withTransformFetcherOpt(fOpt))

  def withClassifiers(classifiers: Set[Classifier]): Fetch[F] =
    withArtifacts(artifacts.withClassifiers(classifiers))
  def addClassifiers(classifiers: Classifier*): Fetch[F] =
    withArtifacts(artifacts.withClassifiers(artifacts.classifiers ++ classifiers))
  def withMainArtifacts(mainArtifacts: JBoolean): Fetch[F] =
    withArtifacts(artifacts.withMainArtifactsOpt(Option(mainArtifacts).map(x => x)))
  def withMainArtifacts(): Fetch[F] =
    withArtifacts(artifacts.withMainArtifactsOpt(Some(true)))
  def withArtifactTypes(artifactTypes: Set[Type]): Fetch[F] =
    withArtifacts(artifacts.withArtifactTypesOpt(Some(artifactTypes)))
  def addArtifactTypes(artifactTypes: Type*): Fetch[F] =
    withArtifacts(artifacts.withArtifactTypesOpt(
      Some(artifacts.artifactTypesOpt.getOrElse(Set()) ++ artifactTypes)
    ))
  def allArtifactTypes(): Fetch[F] =
    withArtifacts(artifacts.withArtifactTypesOpt(Some(Set(Type.all))))

  def withArtifactAttributes(attributes: Seq[VariantSelector.AttributesBased]): Fetch[F] =
    withArtifacts(artifacts.withAttributes(attributes))

  def addExtraArtifacts(f: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(artifacts.extraArtifactsSeq :+ f))
  def noExtraArtifacts(): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(Nil))
  def withExtraArtifacts(
    l: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]]
  ): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(l))

  def addTransformArtifacts(
    f: Seq[(Dependency, Publication, Artifact)] => Seq[(Dependency, Publication, Artifact)]
  ): Fetch[F] =
    withArtifacts(artifacts.addTransformArtifacts(f))

  def withClasspathOrder(classpathOrder: Boolean): Fetch[F] =
    withArtifacts(artifacts.withClasspathOrder(classpathOrder))

  def withGradleModuleSupport(enable: Boolean): Fetch[F] =
    withResolve(resolve.withGradleModuleSupport(Some(enable)))

  /** Add variant attributes to be taken into account when picking Gradle Module variants
    */
  def addVariantAttributes(attributes: (String, VariantSelector.VariantMatcher)*): Fetch[F] =
    withResolve {
      resolve.withResolutionParams(
        resolve.resolutionParams.addVariantAttributes(attributes: _*)
      )
    }

  def ioResult: F[Fetch.Result] = {

    val resolutionIO = resolve.io

    resolutionIO.flatMap { resolution =>
      val fetchIO_ = artifacts
        .withResolution(resolution)
        .ioResult
      S.map(fetchIO_) { res =>
        Fetch.Result(resolution, res.fullDetailedArtifacts0, res.fullExtraArtifacts)
      }
    }
  }

  def io: F[Seq[File]] = {

    val cacheKeyOpt0 = for {
      fetchCache <- fetchCacheOpt
      key        <- cacheKeyOpt
    } yield {
      val cache = FetchCache(fetchCache.toPath)
      (cache, key)
    }

    cacheKeyOpt0 match {
      case Some((cache, key)) =>
        cache.read(key) match {
          case Some(files) =>
            S.point(files)
          case None =>
            ioResult.flatMap { res =>
              val artifacts = res.artifacts
              val files     = res.files

              val maybeWrite =
                if (artifacts.forall(!_._1.changing))
                  S.delay[Unit](cache.write(key, files))
                else
                  S.point(())

              S.map(maybeWrite)(_ => files)
            }
        }
      case None =>
        S.map(ioResult)(_.files)
    }
  }

}

object Fetch {

  @data class Result(
    resolution: Resolution = Resolution(),
    fullDetailedArtifacts0: Seq[(
      Dependency,
      Either[VariantPublication, Publication],
      Artifact,
      Option[File]
    )] = Nil,
    fullExtraArtifacts: Seq[(Artifact, Option[File])] = Nil
  ) {

    def detailedArtifacts0
      : Seq[(Dependency, Either[VariantPublication, Publication], Artifact, File)] =
      fullDetailedArtifacts0.collect {
        case (dep, pub, art, Some(file)) =>
          (dep, pub, art, file)
      }

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
      fullExtraArtifacts
        .collect {
          case (art, Some(file)) =>
            (art, file)
        }
        .distinct

    def artifacts: Seq[(Artifact, File)] =
      fullArtifacts
        .collect {
          case (art, Some(file)) =>
            (art, file)
        }

    def fullArtifacts: Seq[(Artifact, Option[File])] = {
      val artifacts = fullDetailedArtifacts0.map { case (_, _, a, f) => (a, f) } ++
        fullExtraArtifacts
      artifacts.distinct
    }

    def files: Seq[File] =
      artifacts
        .map(_._2)
        .distinct

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

  def apply(): Fetch[Task] =
    new Fetch(
      Resolve(Cache.default),
      Artifacts(Cache.default),
      None
    )

  def apply[F[_]](cache: Cache[F])(implicit S: Sync[F]): Fetch[F] =
    new Fetch[F](
      Resolve(cache),
      Artifacts(cache),
      None
    )

  implicit class FetchTaskOps(private val fetch: Fetch[Task]) extends AnyVal {

    def futureResult()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Future[Result] =
      fetch.ioResult.future()

    def future()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Future[Seq[File]] =
      fetch.io.future()

    def eitherResult()(implicit
      ec: ExecutionContext = fetch.resolve.cache.ec
    ): Either[CoursierError, Result] = {

      val f = fetch
        .ioResult
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def either()(implicit
      ec: ExecutionContext = fetch.resolve.cache.ec
    ): Either[CoursierError, Seq[File]] = {

      val f = fetch
        .io
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def runResult()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Result = {
      val f = fetch.ioResult.future()
      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Seq[File] = {
      val f = fetch.io.future()
      Await.result(f, Duration.Inf)
    }

  }

}
