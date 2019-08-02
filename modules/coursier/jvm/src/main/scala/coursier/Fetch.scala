package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{Cache, FileCache}
import coursier.core.Publication
import coursier.error.CoursierError
import coursier.internal.FetchCache
import coursier.params.{Mirror, ResolutionParams}
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

final class Fetch[F[_]] private (
  private val resolveParams: Resolve.Params[F],
  private val artifactsParams: Artifacts.Params[F],
  private val fetchParams: Fetch.Params
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Fetch[_] =>
        resolveParams == other.resolveParams && artifactsParams == other.artifactsParams
    }

  override def hashCode(): Int =
    37 * (17 + resolveParams.##) + artifactsParams.##

  override def toString: String =
    s"Fetch($resolveParams, $artifactsParams)"


  def dependencies: Seq[Dependency] =
    resolveParams.dependencies
  def repositories: Seq[Repository] =
    resolveParams.repositories
  def mirrors: Seq[Mirror] =
    resolveParams.mirrors
  def resolutionParams: ResolutionParams =
    resolveParams.resolutionParams
  def cache: Cache[F] =
    resolveParams.cache
  def throughOpt: Option[F[Resolution] => F[Resolution]] =
    resolveParams.throughOpt
  def transformFetcherOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]] =
    resolveParams.transformFetcherOpt
  def S: Sync[F] =
    resolveParams.S

  def classifiers: Set[Classifier] =
    artifactsParams.classifiers
  def mainArtifactsOpt: Option[Boolean] =
    artifactsParams.mainArtifactsOpt
  def artifactTypesOpt: Option[Set[Type]] =
    artifactsParams.artifactTypesOpt
  def extraArtifactsSeq: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]] =
    artifactsParams.extraArtifactsSeq

  def classpathOrder: Boolean =
    artifactsParams.classpathOrder

  private def cacheKeyOpt: Option[FetchCache.Key] = {

    val mayBeCached =
      resolveParams.throughOpt.isEmpty &&
        resolveParams.transformFetcherOpt.isEmpty &&
        artifactsParams.extraArtifactsSeq.isEmpty &&
        artifactsParams.resolutions.isEmpty

    if (mayBeCached)
      artifactsParams.cache match {
        case f: FileCache[F] =>
          val key = FetchCache.Key(
            resolveParams.finalDependencies,
            resolveParams.repositories,
            resolveParams
              .resolutionParams
              // taken into account in resolveParams.finalDependencies
              .withExclusions(Set())
              // these are taken into account below
              .withForceVersion(Map())
              .withProperties(Nil)
              .withForcedProperties(Map())
              .withProfiles(Set()),
            resolveParams.resolutionParams.forceVersion.toVector.sortBy { case (m, v) => s"$m:$v" },
            resolveParams.resolutionParams.properties.toVector.sortBy { case (k, v) => s"$k=$v" },
            resolveParams.resolutionParams.forcedProperties.toVector.sortBy { case (k, v) => s"$k=$v" },
            resolveParams.resolutionParams.profiles.toVector.sorted,
            f.location.getAbsolutePath,
            artifactsParams.classifiers.toVector.sorted,
            artifactsParams.mainArtifactsOpt,
            artifactsParams.artifactTypesOpt.map(_.toVector.sorted)
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


  private def withResolveParams(resolveParams: Resolve.Params[F]): Fetch[F] =
    new Fetch(resolveParams, artifactsParams, fetchParams)
  private def withArtifactsParams(artifactsParams: Artifacts.Params[F]): Fetch[F] =
    new Fetch(resolveParams, artifactsParams, fetchParams)
  private def withFetchParams(fetchParams: Fetch.Params): Fetch[F] =
    new Fetch(resolveParams, artifactsParams, fetchParams)

  def withDependencies(dependencies: Seq[Dependency]): Fetch[F] =
    withResolveParams(resolveParams.copy(dependencies = dependencies))
  def addDependencies(dependencies: Dependency*): Fetch[F] =
    withResolveParams(resolveParams.copy(dependencies = resolveParams.dependencies ++ dependencies))

  def withRepositories(repositories: Seq[Repository]): Fetch[F] =
    withResolveParams(resolveParams.copy(repositories = repositories))
  def addRepositories(repositories: Repository*): Fetch[F] =
    withResolveParams(resolveParams.copy(repositories = resolveParams.repositories ++ repositories))

  def noMirrors: Fetch[F] =
    withResolveParams(resolveParams.copy(
      mirrors = Nil,
      mirrorConfFiles = Nil
    ))

  def withMirrors(mirrors: Seq[Mirror]): Fetch[F] =
    withResolveParams(resolveParams.copy(mirrors = mirrors))
  def addMirrors(mirrors: Mirror*): Fetch[F] =
    withResolveParams(resolveParams.copy(mirrors = resolveParams.mirrors ++ mirrors))

  def withResolutionParams(resolutionParams: ResolutionParams): Fetch[F] =
    withResolveParams(resolveParams.copy(resolutionParams = resolutionParams))
  def mapResolutionParams(f: ResolutionParams => ResolutionParams): Fetch[F] =
    withResolveParams(resolveParams.copy(resolutionParams = f(resolutionParams)))

  def withCache(cache: Cache[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(cache = cache))
      .withArtifactsParams(artifactsParams.copy(cache = cache))

  def withResolveCache(cache: Cache[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(cache = cache))
  def withArtifactsCache(cache: Cache[F]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(cache = cache))

  def withOtherArtifactsCaches(caches: Seq[Cache[F]]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(otherCaches = caches))

  def withFetchCache(location: File): Fetch[F] =
    withFetchParams(fetchParams.copy(fetchCacheOpt = Some(location)))
  def withFetchCache(locationOpt: Option[File]): Fetch[F] =
    withFetchParams(fetchParams.copy(fetchCacheOpt = locationOpt))

  def transformResolution(f: F[Resolution] => F[Resolution]): Fetch[F] =
    withResolveParams(resolveParams.copy(throughOpt = Some(resolveParams.throughOpt.fold(f)(_ andThen f))))
  def noTransformResolution(): Fetch[F] =
    withResolveParams(resolveParams.copy(throughOpt = None))
  def withTransformResolution(fOpt: Option[F[Resolution] => F[Resolution]]): Fetch[F] =
    withResolveParams(resolveParams.copy(throughOpt = fOpt))

  def transformFetcher(f: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(transformFetcherOpt = Some(resolveParams.transformFetcherOpt.fold(f)(_ andThen f))))
  def noTransformFetcher(): Fetch[F] =
    withResolveParams(resolveParams.copy(transformFetcherOpt = None))
  def withTransformFetcher(fOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]]): Fetch[F] =
    withResolveParams(resolveParams.copy(transformFetcherOpt = fOpt))

  def withClassifiers(classifiers: Set[Classifier]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(classifiers = classifiers))
  def addClassifiers(classifiers: Classifier*): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(classifiers = artifactsParams.classifiers ++ classifiers))
  def withMainArtifacts(mainArtifacts: JBoolean): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(mainArtifactsOpt = Option(mainArtifacts).map(x => x)))
  def withMainArtifacts(): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(mainArtifactsOpt = Some(true)))
  def withArtifactTypes(artifactTypes: Set[Type]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(artifactTypesOpt = Some(artifactTypes)))
  def addArtifactTypes(artifactTypes: Type*): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(artifactTypesOpt = Some(artifactsParams.artifactTypesOpt.getOrElse(Set()) ++ artifactTypes)))
  def allArtifactTypes(): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(artifactTypesOpt = Some(Set(Type.all))))

  def addExtraArtifacts(f: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(extraArtifactsSeq = artifactsParams.extraArtifactsSeq :+ f))
  def noExtraArtifacts(): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(extraArtifactsSeq = Nil))
  def withExtraArtifacts(l: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(extraArtifactsSeq = l))

  def withClasspathOrder(classpathOrder: Boolean): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(classpathOrder = classpathOrder))

  def ioResult: F[Fetch.Result] = {

    val resolutionIO = new Resolve(resolveParams).io

    S.bind(resolutionIO) { resolution =>
      val fetchIO_ = new Artifacts(artifactsParams)
        .withResolution(resolution)
        .ioResult
      S.map(fetchIO_) { res =>
        Fetch.Result(resolution, res.detailedArtifacts, res.extraArtifacts)
      }
    }
  }

  def io: F[Seq[File]] = {

    val cacheKeyOpt0 = for {
      fetchCache <- fetchParams.fetchCacheOpt
      key <- cacheKeyOpt
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
            S.bind(ioResult) { res =>
              val artifacts = res.artifacts
              val files = res.files

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

  final class Result private (
    val resolution: Resolution,
    val detailedArtifacts: Seq[(Dependency, Publication, Artifact, File)],
    val extraArtifacts: Seq[(Artifact, File)]
  ) {

    override def equals(obj: Any): Boolean =
      obj match {
        case other: Result =>
          resolution == other.resolution &&
            detailedArtifacts == other.detailedArtifacts &&
            extraArtifacts == other.extraArtifacts
        case _ => false
      }

    override def hashCode(): Int = {
      var code = 17 + "coursier.Fetch.Result".##
      code = 37 * code + resolution.##
      code = 37 * code + detailedArtifacts.##
      code = 37 * code + extraArtifacts.##
      code
    }

    override def toString: String =
      s"Fetch.Result($resolution, $detailedArtifacts, $extraArtifacts)"


    def artifacts: Seq[(Artifact, File)] =
      detailedArtifacts.map { case (_, _, a, f) => (a, f) } ++ extraArtifacts

    def files: Seq[File] =
      artifacts.map(_._2)

  }

  object Result {
    def apply(
      resolution: Resolution,
      detailedArtifacts: Seq[(Dependency, Publication, Artifact, File)],
      extraArtifacts: Seq[(Artifact, File)]
    ): Result =
      new Result(
        resolution,
        detailedArtifacts,
        extraArtifacts
      )
  }

  // see Resolve.apply for why cache is passed here
  def apply[F[_]](cache: Cache[F] = Cache.default)(implicit S: Sync[F]): Fetch[F] =
    new Fetch[F](
      Resolve.defaultParams(cache),
      Artifacts.Params(
        Nil,
        Set(),
        None,
        None,
        cache,
        Nil,
        Nil,
        classpathOrder = true,
        S
      ),
      Params(
        None
      )
    )

  implicit class FetchTaskOps(private val fetch: Fetch[Task]) extends AnyVal {

    def futureResult()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Future[Result] =
      fetch.ioResult.future()

    def future()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Future[Seq[File]] =
      fetch.io.future()

    def eitherResult()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Either[CoursierError, Result] = {

      val f = fetch
        .ioResult
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def either()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Either[CoursierError, Seq[File]] = {

      val f = fetch
        .io
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def runResult()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Result = {
      val f = fetch.ioResult.future()
      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Seq[File] = {
      val f = fetch.io.future()
      Await.result(f, Duration.Inf)
    }

  }

  private final case class Params(
    fetchCacheOpt: Option[File]
  )

}
