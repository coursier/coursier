package coursier.util

import coursier.util.internal.ConcurrentReferenceHashMap

object Cache {
  def createCache[T >: Null](): ConcurrentReferenceHashMap[T, T] =
    new ConcurrentReferenceHashMap[T, T](8, ConcurrentReferenceHashMap.ReferenceType.WEAK, ConcurrentReferenceHashMap.ReferenceType.WEAK)
  def cacheMethod[T >: Null](instanceCache: ConcurrentReferenceHashMap[T, T])(t: T): T = {
   val first = instanceCache.get(t)
   if (first == null) {
     val previous = instanceCache.putIfAbsent(t, t)
     if (previous == null) t else previous
   } else
     first
 }
}
