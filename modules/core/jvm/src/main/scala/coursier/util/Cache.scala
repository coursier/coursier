package coursier.util

import java.lang.ref.WeakReference

object Cache {
  def createCache[K, V >: Null](): java.util.Map[K, WeakReference[V]] = new java.util.WeakHashMap[K, java.lang.ref.WeakReference[V]]()
  def xpto[K, V >: Null](memoised_cache: java.util.Map[K, java.lang.ref.WeakReference[V]])(key: K, f: K => V, keyFn: V => K): V = {
   val first: V = {
     val weak = memoised_cache.get(key)
     if (weak == null) null else weak.get
   }
   if (first != null) {
     first
   } else {
     memoised_cache.synchronized {
       val got: V = {
         val weak = memoised_cache.get(key)
         if (weak == null) {
           null
         } else {
           val ref = weak.get
           ref
         }
       }
       if (got != null) {
         got
       } else {
         val created = f(key)
         val weakRef = new _root_.java.lang.ref.WeakReference(created)
         //it is important to use created.key as the key in WeakHashMap
         memoised_cache.put(keyFn(created), weakRef)
         created
       }
     }
   }
 }
}
