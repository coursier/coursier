package coursier.util

import coursier.util.internal.ConcurrentReferenceHashMap

object Cache {
  def createCache[T >: Null](): ConcurrentReferenceHashMap[T, T] = null
  def cacheMethod[T >: Null](instanceCache: ConcurrentReferenceHashMap[T, T])(t: T): T = t
}
