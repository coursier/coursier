package coursier.util

import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.collection.compat._
import scala.collection.generic.Growable

private[coursier] abstract class HashMapBuilder[A, B <: AnyRef] {
  def add(a: A, B: B): this.type
  def addOne(elem: (A, B)): this.type
  def addAll(elems: IterableOnce[(A, B)]): this.type
  def getOrNull(a: A): B
  def result(): HashMap[A, B]
}

private[coursier] object HashMapBuilder {
  implicit class toGrowableExtensionMethods[A](val self: Growable[A]) extends AnyVal {
    def addOne(elem: A): self.type = {
      self += elem
      self
    }
    def addAll(elems: IterableOnce[A]): self.type = {
      self ++= elems
      self
    }
  }
}

private[coursier] final class CompatibleHashMapBuilder[A, B <: AnyRef]
    extends HashMapBuilder[A, B] {
  import HashMapBuilder.toGrowableExtensionMethods
  private val delegate            = new mutable.HashMap[A, B]
  override def getOrNull(a: A): B = delegate.getOrElse(a, null.asInstanceOf[B])

  def clear(): Unit = delegate.clear()

  def result(): HashMap[A, B] =
    HashMap.from(delegate)

  def add(a: A, b: B): CompatibleHashMapBuilder.this.type = {
    delegate(a) = b
    this
  }

  def addOne(elem: (A, B)): CompatibleHashMapBuilder.this.type = {
    delegate(elem._1) = elem._2
    this
  }

  def addAll(elems: IterableOnce[(A, B)]): CompatibleHashMapBuilder.this.type = {
    delegate.addAll(elems)
    this
  }
}
