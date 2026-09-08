package coursier.util

import java.util.concurrent.ConcurrentMap

import concurrentrefhashmap.ConcurrentReferenceHashMap

private[coursier] object Cache {
  def createCache[T >: Null](): ConcurrentMap[T, T] =
    new ConcurrentReferenceHashMap[T, T](
      8,
      ConcurrentReferenceHashMap.ReferenceType.WEAK,
      ConcurrentReferenceHashMap.ReferenceType.WEAK
    )
  def cacheMethod[T >: Null](instanceCache: ConcurrentMap[T, T])(t: T): T = {
    val first = instanceCache.get(t)
    if (first == null) {
      val previous = instanceCache.putIfAbsent(t, t)
      if (previous == null) t else previous
    }
    else
      first
  }
  def createMemoCache[K >: Null, V >: Null](): ConcurrentMap[K, V] =
    new ConcurrentReferenceHashMap[K, V](
      16,
      ConcurrentReferenceHashMap.ReferenceType.SOFT,
      ConcurrentReferenceHashMap.ReferenceType.STRONG
    )
  def memoizeMethod[K >: Null, V >: Null](memoCache: ConcurrentMap[K, V])(key: K)(f: K => V): V = {
    val cached = memoCache.get(key)
    if (cached == null) {
      val computed = f(key)
      val previous = memoCache.putIfAbsent(key, computed)
      if (previous == null) computed else previous
    }
    else
      cached
  }
}
