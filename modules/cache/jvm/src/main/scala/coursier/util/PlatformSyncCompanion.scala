package coursier.util

import coursier.cache.internal.ThreadUtil

import java.util.concurrent.ExecutorService

abstract class PlatformSyncCompanion {

  private[coursier] def fixedThreadPool(size: Int): ExecutorService =
    ThreadUtil.fixedThreadPool(size)

  private[coursier] def withFixedThreadPool[T](size: Int)(f: ExecutorService => T): T =
    ThreadUtil.withFixedThreadPool(size)(f)

}
