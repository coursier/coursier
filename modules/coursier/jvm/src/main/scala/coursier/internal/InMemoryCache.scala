package coursier.internal

import java.io.File

import coursier.cache.{ArtifactError, Cache, CacheLogger}
import coursier.util.{Artifact, EitherT, Sync}

import scala.concurrent.ExecutionContext
import dataclass.data

@data class InMemoryCache[F[_]](underlying: Cache[F], S: Sync[F]) extends Cache[F] {

  private implicit def S0 = S

  def fetch: Cache.Fetch[F] =
    new InMemoryCachingFetcher(underlying.fetch).fetcher

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    underlying.file(artifact)

  def ec: ExecutionContext =
    underlying.ec

  override def loggerOpt: Option[CacheLogger] =
    underlying.loggerOpt
}
