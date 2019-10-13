package coursier.params

import java.io.File
import java.util.concurrent.ExecutorService

import coursier.cache._
import coursier.credentials.Credentials
import coursier.internal.InMemoryCache
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

final case class CacheParams(
  cacheLocation: java.io.File,
  cachePolicies: Seq[coursier.cache.CachePolicy],
  ttl: Option[scala.concurrent.duration.Duration],
  parallel: Int,
  checksum: Seq[Option[String]],
  retryCount: Int,
  cacheLocalArtifacts: Boolean,
  followHttpToHttpsRedirections: Boolean,
  credentials: Seq[coursier.credentials.Credentials] = Nil,
  useEnvCredentials: Boolean = true
) {

  def withCacheLocation(cacheLocation: java.io.File): CacheParams =
    copy(cacheLocation = cacheLocation)
  def withCachePolicies(cachePolicies: Seq[coursier.cache.CachePolicy]): CacheParams =
    copy(cachePolicies = cachePolicies)
  def withTtl(ttl: Option[scala.concurrent.duration.Duration]): CacheParams =
    copy(ttl = ttl)
  def withTtl(ttl: scala.concurrent.duration.Duration): CacheParams =
    copy(ttl = Option(ttl))
  def withParallel(parallel: Int): CacheParams =
    copy(parallel = parallel)
  def withChecksum(checksum: Seq[Option[String]]): CacheParams =
    copy(checksum = checksum)
  def withRetryCount(retryCount: Int): CacheParams =
    copy(retryCount = retryCount)
  def withCacheLocalArtifacts(cacheLocalArtifacts: Boolean): CacheParams =
    copy(cacheLocalArtifacts = cacheLocalArtifacts)
  def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections: Boolean): CacheParams =
    copy(followHttpToHttpsRedirections = followHttpToHttpsRedirections)
  def withCredentials(credentials: Seq[coursier.credentials.Credentials]): CacheParams =
    copy(credentials = credentials)
  def withUseEnvCredentials(useEnvCredentials: Boolean): CacheParams =
    copy(useEnvCredentials = useEnvCredentials)

  def cache[F[_]](
    pool: ExecutorService,
    logger: CacheLogger,
    inMemoryCache: Boolean = false
  )(implicit S: Sync[F] = Task.sync): Cache[F] = {

    var c = FileCache[F]()
      .withLocation(cacheLocation)
      .withCachePolicies(cachePolicies)
      .withChecksums(checksum)
      .withLogger(logger)
      .withPool(pool)
      .withTtl(ttl)
      .withRetry(retryCount)
      .withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections)
      .withLocalArtifactsShouldBeCached(cacheLocalArtifacts)

    if (!useEnvCredentials)
      c = c.withCredentials(Nil)

    c = c.addCredentials(credentials: _*)

    if (inMemoryCache)
      InMemoryCache(c, S)
    else
      c
  }
}
