package coursier.internal

import java.io.File

import coursier.cache.{ArtifactError, Cache, CacheLogger}
import coursier.core.{Artifact, Repository}
import coursier.util.{EitherT, Schedulable}

import scala.concurrent.ExecutionContext

final case class InMemoryCache[F[_]](underlying: Cache[F], S: Schedulable[F]) extends Cache[F] {

  private implicit def S0 = S

  def fetch: Repository.Fetch[F] =
    new InMemoryCachingFetcher(underlying.fetch).fetcher

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    underlying.file(artifact)

  def ec: ExecutionContext =
    underlying.ec

  override def loggerOpt: Option[CacheLogger] =
    underlying.loggerOpt
}
