package coursier.util

import java.lang.ref.WeakReference

object Cache {
  def createCache[K, V >: Null](): java.util.Map[K, WeakReference[V]] = null

  def cacheMethod[K, V >: Null](memoised_cache: java.util.Map[K, java.lang.ref.WeakReference[V]])(key: K, f: K => V, keyFn: V => K): V = {
    f(key)
  }
}
