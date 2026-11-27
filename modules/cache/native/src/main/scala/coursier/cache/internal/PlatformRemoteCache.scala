package coursier.cache.internal

import coursier.cache.{Cache, FileCache}

/** `RemoteCache` is JVM-only, so a [[FileCache]] never defers to a cache server here */
private[cache] object PlatformRemoteCache {
  def substituteFor[F[_]](fileCache: FileCache[F]): Option[Cache[F]] =
    None
}
