package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.{Cache, FileCache}
import coursier.core.Publication
import coursier.error.CoursierError
import coursier.internal.FetchCache
import coursier.params.{Mirror, ResolutionParams}
import coursier.util.{Artifact, Sync, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

final class Fetch[F[_]] private (
  private val resolve: Resolve[F],
  private val artifacts: Artifacts[F],
  private val fetchParams: Fetch.Params
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Fetch[_] =>
        resolve == other.resolve && artifacts == other.artifacts
    }

  override def hashCode(): Int =
    37 * (17 + resolve.##) + artifacts.##

  override def toString: String =
    s"Fetch($resolve, $artifacts)"


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
  def transformFetcherOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]] =
    resolve.transformFetcherOpt
  def S: Sync[F] =
    resolve.S

  def classifiers: Set[Classifier] =
    artifacts.classifiers
  def mainArtifactsOpt: Option[Boolean] =
    artifacts.mainArtifactsOpt
  def artifactTypesOpt: Option[Set[Type]] =
    artifacts.artifactTypesOpt
  def extraArtifactsSeq: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]] =
    artifacts.extraArtifactsSeq

  def classpathOrder: Boolean =
    artifacts.classpathOrder

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
              .withForceVersion(Map())
              .withProperties(Nil)
              .withForcedProperties(Map())
              .withProfiles(Set()),
            resolve.resolutionParams.forceVersion.toVector.sortBy { case (m, v) => s"$m:$v" },
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


  private def withResolve(resolve: Resolve[F]): Fetch[F] =
    new Fetch(resolve, artifacts, fetchParams)
  private def withArtifacts(artifacts: Artifacts[F]): Fetch[F] =
    new Fetch(resolve, artifacts, fetchParams)
  private def withFetchParams(fetchParams: Fetch.Params): Fetch[F] =
    new Fetch(resolve, artifacts, fetchParams)

  def withDependencies(dependencies: Seq[Dependency]): Fetch[F] =
    withResolve(resolve.withDependencies(dependencies))
  def addDependencies(dependencies: Dependency*): Fetch[F] =
    withResolve(resolve.withDependencies(resolve.dependencies ++ dependencies))

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
    withFetchParams(fetchParams.copy(fetchCacheOpt = Some(location)))
  def withFetchCache(locationOpt: Option[File]): Fetch[F] =
    withFetchParams(fetchParams.copy(fetchCacheOpt = locationOpt))

  def transformResolution(f: F[Resolution] => F[Resolution]): Fetch[F] =
    withResolve(resolve.withThroughOpt(Some(resolve.throughOpt.fold(f)(_ andThen f))))
  def noTransformResolution(): Fetch[F] =
    withResolve(resolve.withThroughOpt(None))
  def withTransformResolution(fOpt: Option[F[Resolution] => F[Resolution]]): Fetch[F] =
    withResolve(resolve.withThroughOpt(fOpt))

  def transformFetcher(f: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]): Fetch[F] =
    withResolve(resolve.withTransformFetcherOpt(Some(resolve.transformFetcherOpt.fold(f)(_ andThen f))))
  def noTransformFetcher(): Fetch[F] =
    withResolve(resolve.withTransformFetcherOpt(None))
  def withTransformFetcher(fOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]]): Fetch[F] =
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
    withArtifacts(artifacts.withArtifactTypesOpt(Some(artifacts.artifactTypesOpt.getOrElse(Set()) ++ artifactTypes)))
  def allArtifactTypes(): Fetch[F] =
    withArtifacts(artifacts.withArtifactTypesOpt(Some(Set(Type.all))))

  def addExtraArtifacts(f: Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(artifacts.extraArtifactsSeq :+ f))
  def noExtraArtifacts(): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(Nil))
  def withExtraArtifacts(l: Seq[Seq[(Dependency, Publication, Artifact)] => Seq[Artifact]]): Fetch[F] =
    withArtifacts(artifacts.withExtraArtifactsSeq(l))

  def withClasspathOrder(classpathOrder: Boolean): Fetch[F] =
    withArtifacts(artifacts.withClasspathOrder(classpathOrder))

  def ioResult: F[Fetch.Result] = {

    val resolutionIO = resolve.io

    S.bind(resolutionIO) { resolution =>
      val fetchIO_ = artifacts
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
      Resolve(cache),
      Artifacts(cache),
      Params(
        None
      )
    )

  implicit class FetchTaskOps(private val fetch: Fetch[Task]) extends AnyVal {

    def futureResult()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Future[Result] =
      fetch.ioResult.future()

    def future()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Future[Seq[File]] =
      fetch.io.future()

    def eitherResult()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Either[CoursierError, Result] = {

      val f = fetch
        .ioResult
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def either()(implicit ec: ExecutionContext = fetch.resolve.cache.ec): Either[CoursierError, Seq[File]] = {

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

  private final case class Params(
    fetchCacheOpt: Option[File]
  )

}
