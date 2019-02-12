package coursier.params

import java.io.File
import java.util.concurrent.ExecutorService

import coursier.cache.{Cache, CacheDefaults, CacheLogger, CachePolicy, FileCache}
import coursier.util.{Schedulable, Task}

import scala.concurrent.duration.Duration

final case class CacheParams(
  cacheLocation: File = CacheDefaults.location, // directory, existing or that can be created
  cachePolicies: Seq[CachePolicy] = CachePolicy.default, // non-empty
  ttl: Option[Duration] = CacheDefaults.ttl,
  parallel: Int = CacheDefaults.concurrentDownloadCount, // FIXME Move elsewhere?
  checksum: Seq[Option[String]] = CacheDefaults.checksums,
  retryCount: Int = 1,
  cacheLocalArtifacts: Boolean = false,
  followHttpToHttpsRedirections: Boolean = true
) {
  def cache[F[_]](
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F] = Task.schedulable): Cache[F] =
    FileCache[F](
      cacheLocation,
      cachePolicies,
      checksums = checksum,
      logger = Some(logger),
      pool = pool,
      ttl = ttl,
      retry = retryCount,
      followHttpToHttpsRedirections = followHttpToHttpsRedirections,
      localArtifactsShouldBeCached = cacheLocalArtifacts,
      S = S
    )

}
