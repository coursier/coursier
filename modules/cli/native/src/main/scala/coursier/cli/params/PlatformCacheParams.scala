package coursier.cli.params

import java.util.concurrent.ExecutorService

import coursier.cache.{Cache, CacheLogger, FileCache}
import coursier.util.Task

import scala.concurrent.duration.Duration

trait PlatformCacheParams { self: CacheParams =>

  /** `RemoteCache` is JVM-only, so `Cache.default` is always a `FileCache` here, and this is never
    * called.
    */
  protected def customizeOtherCache(
    cache: Cache.Default[Task],
    pool: ExecutorService,
    logger: CacheLogger,
    overrideTtl: Option[Duration],
    basicCustomizations: FileCache[Task] => FileCache[Task]
  ): Cache.Default[Task] =
    cache
}
