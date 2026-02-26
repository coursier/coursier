package coursier.internal

import coursier.cache.{ArtifactError, Cache, CacheLogger}
import coursier.util.{Artifact, EitherT, Sync}
import dataclass.data

import java.io.File

import scala.concurrent.ExecutionContext

@data class InMemoryCache[F[_]](underlying: Cache[F], S: Sync[F]) extends Cache[F] {

  private implicit def S0: Sync[F] = S

  def fetch: Cache.Fetch[F] =
    new InMemoryCachingFetcher(underlying.fetch).fetcher

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    underlying.file(artifact)

  def ec: ExecutionContext =
    underlying.ec

  override def loggerOpt: Option[CacheLogger] =
    underlying.loggerOpt
}
