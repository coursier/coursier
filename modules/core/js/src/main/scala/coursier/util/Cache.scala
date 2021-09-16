package coursier.util

import java.util.concurrent.ConcurrentMap

private[coursier] object Cache {
  def createCache[T >: Null](): ConcurrentMap[T, T]                       = null
  def cacheMethod[T >: Null](instanceCache: ConcurrentMap[T, T])(t: T): T = t
}
