package coursier.cache

import coursier.util.{Sync, Task}

import java.io.File
import scala.concurrent.ExecutionContextExecutorService

abstract class PlatformCacheCompanion {

  final type Default[F[_]] = PlatformCacheCompanion.Default[F]

  def defaultLocalCacheFor[F[_]: Sync]: FileCache[F] =
    FileCache[F](CacheDefaults.location)

  def defaultFor[F[_]: Sync]: Default[F] =
    CacheDefaults.cacheServerAddress match {
      case Some(serverAddress) =>
        RemoteCache(serverAddress, CacheDefaults.location)
          .withBasicAuth(CacheDefaults.cacheServerBasicAuth)
      case None =>
        defaultLocalCacheFor[F]
    }

  def defaultLocalCache: FileCache[Task] =
    defaultLocalCacheFor[Task]

  lazy val default: Default[Task] =
    defaultFor[Task]

  trait HasLocation {
    def location: File
  }

  trait WithLogger[F[_], +Repr] {
    def logger: CacheLogger
    def withLogger(logger: CacheLogger): Repr
  }

  trait HasExecutionContext {
    def ec: ExecutionContextExecutorService
  }

}

object PlatformCacheCompanion {

  trait Default[F[_]] extends Cache[F] with Cache.HasLocation with Cache.HasExecutionContext
      with Cache.WithLogger[F, Default[F]]

}
