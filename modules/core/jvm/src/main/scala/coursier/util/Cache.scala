package coursier.util

import java.util.concurrent.ConcurrentMap

import concurrentrefhashmap.ConcurrentReferenceHashMap

private[coursier] object Cache {
  def createCache[T >: Null](): ConcurrentMap[T, T] =
    new ConcurrentReferenceHashMap[T, T](8, ConcurrentReferenceHashMap.ReferenceType.WEAK, ConcurrentReferenceHashMap.ReferenceType.WEAK)
  def cacheMethod[T >: Null](instanceCache: ConcurrentMap[T, T])(t: T): T = {
   val first = instanceCache.get(t)
   if (first == null) {
     val previous = instanceCache.putIfAbsent(t, t)
     if (previous == null) t else previous
   } else
     first
 }
}
