package coursier.params

import java.io.File
import java.util.concurrent.ExecutorService

import coursier.cache._
import coursier.util.{Schedulable, Task}

import scala.concurrent.duration.Duration

abstract class CacheParamsHelpers {

  def cacheLocation: File
  def cachePolicies: Seq[CachePolicy]
  def ttl: Option[Duration]
  def checksum: Seq[Option[String]]
  def retryCount: Int
  def cacheLocalArtifacts: Boolean
  def followHttpToHttpsRedirections: Boolean

  def cache[F[_]](
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F] = Task.schedulable): Cache[F] =
    FileCache[F](
      cacheLocation,
      cachePolicies,
      checksums = checksum,
      logger = logger,
      pool = pool,
      ttl = ttl,
      retry = retryCount,
      followHttpToHttpsRedirections = followHttpToHttpsRedirections,
      localArtifactsShouldBeCached = cacheLocalArtifacts,
      S = S
    )

}
