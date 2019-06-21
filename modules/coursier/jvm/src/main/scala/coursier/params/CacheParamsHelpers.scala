package coursier.params

import java.io.File
import java.util.concurrent.ExecutorService

import coursier.cache._
import coursier.credentials.Credentials
import coursier.internal.InMemoryCache
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration

abstract class CacheParamsHelpers {

  def cacheLocation: File
  def cachePolicies: Seq[CachePolicy]
  def ttl: Option[Duration]
  def checksum: Seq[Option[String]]
  def retryCount: Int
  def cacheLocalArtifacts: Boolean
  def followHttpToHttpsRedirections: Boolean
  def credentials: Seq[Credentials]
  def useEnvCredentials: Boolean

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
      .withSync(S)

    if (!useEnvCredentials)
      c = c.withCredentials(Nil)

    c = c.addCredentials(credentials: _*)

    if (inMemoryCache)
      InMemoryCache(c, S)
    else
      c
  }

}
