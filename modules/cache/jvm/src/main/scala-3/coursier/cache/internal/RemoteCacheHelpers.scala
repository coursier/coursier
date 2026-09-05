package coursier.cache.internal

import coursier.cache.{CacheLogger, RemoteCache}

trait RemoteCacheHelpers[F[_]] { self: RemoteCache[F] =>
  def withLogger(logger: CacheLogger): RemoteCache[F] =
    copy(logger = logger)
}
