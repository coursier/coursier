package coursier.cache.internal

import coursier.cache.{Cache, FileCache, RemoteCache}

/** The cache server client a [[FileCache]] defers to, see `FileCache#remoteCacheSubstitute` */
private[cache] object PlatformRemoteCache {
  def substituteFor[F[_]](fileCache: FileCache[F]): Option[Cache[F]] =
    RemoteCache.substituteFor(fileCache, Cache.default)
}
