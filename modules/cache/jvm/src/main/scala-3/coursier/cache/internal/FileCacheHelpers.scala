package coursier.cache.internal

import coursier.cache.{CacheLogger, FileCache}

import java.io.File
import java.util.concurrent.ExecutorService

trait FileCacheHelpers[F[_]] { self: FileCache[F] =>
  def withLogger(logger: CacheLogger): FileCache[F] =
    copy(logger = logger)
}
