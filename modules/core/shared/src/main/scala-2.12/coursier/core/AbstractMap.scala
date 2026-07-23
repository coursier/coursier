package coursier.core

// In 2.12, immutable.Map requires `+` and `-` as abstract instead of 2.13's `updated`/`removed`.
// Bridge them so subclasses implement the 2.13-style API in both versions.
private[coursier] abstract class AbstractMap[K, +V]
    extends scala.collection.immutable.AbstractMap[K, V] {
  def removed(key: K): scala.collection.immutable.Map[K, V]
  def updated[V1 >: V](key: K, value: V1): scala.collection.immutable.Map[K, V1]

  final def -(key: K): scala.collection.immutable.Map[K, V]                = removed(key)
  final def +[V1 >: V](kv: (K, V1)): scala.collection.immutable.Map[K, V1] = updated(kv._1, kv._2)
}
