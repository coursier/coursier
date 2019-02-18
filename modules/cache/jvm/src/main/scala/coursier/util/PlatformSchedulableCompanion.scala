package coursier.util

import java.util.concurrent.ExecutorService

import coursier.cache.internal.ThreadUtil

abstract class PlatformSchedulableCompanion {

  private[coursier] def fixedThreadPool(size: Int): ExecutorService =
    ThreadUtil.fixedThreadPool(size)

}
