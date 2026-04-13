package coursier.util

import scala.collection.immutable.HashMap
import scala.collection.{immutable, mutable}

private[coursier] abstract class HashMapBuilder[A, B <: AnyRef] extends mutable.Builder[(A, B), immutable.HashMap[A, B]] {
  def add(a: A, B: B): this.type
  def getOrNull(a: A): B
}

private[coursier] final class CompatibleHashMapBuilder[A, B <: AnyRef] extends HashMapBuilder[A, B] {
  private val delegate = new mutable.HashMap[A, B]
  override def getOrNull(a: A): B = delegate.getOrElse(a, null.asInstanceOf[B])

  override def clear(): Unit = delegate.clear()

  override def result(): HashMap[A, B] = {
    HashMap.from(delegate)
  }

  override def add(a: A, b: B): CompatibleHashMapBuilder.this.type = {
    delegate(a) = b
    this
  }

  override def addOne(elem: (A, B)): CompatibleHashMapBuilder.this.type = {
    delegate(elem._1) = elem._2
    this
  }

  override def addAll(elems: IterableOnce[(A, B)]): CompatibleHashMapBuilder.this.type = {
    delegate.addAll(elems)
    this
  }
}
