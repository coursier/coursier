package coursier

import java.io.File
import java.lang.{Boolean => JBoolean}

import coursier.cache.Cache
import coursier.core.{Classifier, Type}
import coursier.error.CoursierError
import coursier.params.ResolutionParams
import coursier.util.{Schedulable, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

final class Fetch[F[_]] private (
  private val resolveParams: Resolve.Params[F],
  private val artifactsParams: Artifacts.Params[F]
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

  private def withResolveParams(resolveParams: Resolve.Params[F]): Fetch[F] =
    new Fetch(resolveParams, artifactsParams)
  private def withArtifactsParams(artifactsParams: Artifacts.Params[F]): Fetch[F] =
    new Fetch(resolveParams, artifactsParams)

  def withDependencies(dependencies: Seq[Dependency]): Fetch[F] =
    withResolveParams(resolveParams.copy(dependencies = dependencies))
  def addDependencies(dependencies: Dependency*): Fetch[F] =
    withResolveParams(resolveParams.copy(dependencies = resolveParams.dependencies ++ dependencies))

  def withRepositories(repositories: Seq[Repository]): Fetch[F] =
    withResolveParams(resolveParams.copy(repositories = repositories))
  def addRepositories(repositories: Repository*): Fetch[F] =
    withResolveParams(resolveParams.copy(repositories = resolveParams.repositories ++ repositories))

  def withResolutionParams(resolutionParams: ResolutionParams): Fetch[F] =
    withResolveParams(resolveParams.copy(resolutionParams = resolutionParams))

  def withCache(cache: Cache[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(cache = cache))
      .withArtifactsParams(artifactsParams.copy(cache = cache))

  def withResolveCache(cache: Cache[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(cache = cache))
  def withArtifactsCache(cache: Cache[F]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(cache = cache))

  def transformResolution(f: F[Resolution] => F[Resolution]): Fetch[F] =
    withResolveParams(resolveParams.copy(through = f))
  def transformFetcher(f: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]): Fetch[F] =
    withResolveParams(resolveParams.copy(transformFetcher = f))

  def withClassifiers(classifiers: Set[Classifier]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(classifiers = classifiers))
  def withMainArtifacts(mainArtifacts: JBoolean): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(mainArtifacts = mainArtifacts))
  def withArtifactTypes(artifactTypes: Set[Type]): Fetch[F] =
    withArtifactsParams(artifactsParams.copy(artifactTypes = artifactTypes))

  private def S = resolveParams.S

  def io: F[(Resolution, Seq[(Artifact, File)])] = {

    val resolutionIO = new Resolve(resolveParams).io

    S.bind(resolutionIO) { resolution =>
      val fetchIO_ = new Artifacts(artifactsParams)
        .withResolution(resolution)
        .io
      S.map(fetchIO_) { artifacts =>
        (resolution, artifacts)
      }
    }
  }

}

object Fetch {

  // see Resolve.apply for why cache is passed here
  def apply[F[_]](cache: Cache[F] = Cache.default)(implicit S: Schedulable[F]): Fetch[F] =
    new Fetch[F](
      Resolve.Params(
        Nil,
        Resolve.defaultRepositories,
        ResolutionParams(),
        cache,
        identity,
        identity,
        S
      ),
      Artifacts.Params(
        Resolution(),
        Set(),
        null,
        null,
        cache,
        S
      )
    )

  implicit class FetchTaskOps(private val fetch: Fetch[Task]) extends AnyVal {

    def future()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Future[(Resolution, Seq[(Artifact, File)])] =
      fetch.io.future()

    def either()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): Either[CoursierError, (Resolution, Seq[(Artifact, File)])] = {

      val f = fetch
        .io
        .map(Right(_))
        .handle { case ex: CoursierError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = fetch.resolveParams.cache.ec): (Resolution, Seq[(Artifact, File)]) = {
      val f = fetch.io.future()
      Await.result(f, Duration.Inf)
    }

  }

  private final case class Params[F[_]](
    classifiers: Set[Classifier],
    mainArtifacts: JBoolean,
    artifactTypes: Set[Type]
  )

}
