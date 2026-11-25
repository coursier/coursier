package coursier.cli.params

import java.util.concurrent.ExecutorService

import coursier.cache.{Cache, CacheLogger, FileCache, RemoteCache}
import coursier.internal.InMemoryCache
import coursier.util.Task

import scala.concurrent.duration.Duration

trait PlatformCacheParams { self: CacheParams =>

  protected def customizeOtherCache(
    cache: Cache.Default[Task],
    pool: ExecutorService,
    logger: CacheLogger,
    overrideTtl: Option[Duration],
    basicCustomizations: FileCache[Task] => FileCache[Task]
  ): Cache.Default[Task] =
    cache match {
      case rc: RemoteCache[Task] =>
        rc.copy(
          location = cacheLocation,
          logger = logger,
          pool = pool,
          fileFallback = Some(basicCustomizations(Cache.defaultLocalCache)),
          cachePolicies = cachePolicies,
          ttl = overrideTtl.orElse(ttl)
        )
      // .withLocalArtifactsShouldBeCached(cacheLocalArtifacts)
      case other =>
        // FIXME Warn users if they customize that aren't going to be used
        other
    }

  def cache(
    pool: ExecutorService,
    logger: CacheLogger,
    inMemoryCache: Boolean
  ): Cache[Task] = {

    val c = cache(pool, logger)

    if (inMemoryCache)
      InMemoryCache(c, Task.sync)
    else
      c
  }
}
