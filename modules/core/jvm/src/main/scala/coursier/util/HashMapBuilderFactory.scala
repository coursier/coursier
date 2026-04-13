package coursier.util

import java.lang.invoke.{MethodHandle, MethodHandles, MethodType}
import scala.collection.immutable.HashMap

private[coursier] object HashMapBuilderFactory {
  def apply[A, B <: AnyRef]: HashMapBuilder[A, B] = {
    if (getOrElseMH == null || addOneMH == null) new CompatibleHashMapBuilder[A, B]
    else new FastHashMapBuilder[A, B]
  }
  // Uses non-public API of scala.collection.immutable.HashMapBuilder
  // to allow lookup of values mid-build and to enable tuple-free addOne.
  final class FastHashMapBuilder[A, B <: AnyRef] extends HashMapBuilder[A, B] {
    lazy val delegate = HashMap.newBuilder[A, B]

    override def knownSize: Int = delegate.knownSize

    override def clear(): Unit = delegate.clear()

    override def result(): HashMap[A, B] = delegate.result()

    def getOrNull(a: A): B = {
      getOrElseMH.invoke(delegate, a, null).asInstanceOf[B]
    }

    override def add(a: A, b: B): FastHashMapBuilder.this.type = {
      addOneMH.invoke(delegate, a, b)
      this
    }

    override def addOne(elem: (A, B)): FastHashMapBuilder.this.type = {
      delegate.addOne(elem)
      this
    }

    override def addAll(elems: IterableOnce[(A, B)]): FastHashMapBuilder.this.type = {
      delegate.addAll(elems)
      this
    }
  }

  private val lookup = MethodHandles.lookup()
  private final val getOrElseMH: MethodHandle =
    try {
      lookup.findVirtual(
        Class.forName("scala.collection.immutable.HashMapBuilder"),
        "getOrElse",
        MethodType.methodType(
          classOf[java.lang.Object],
          classOf[java.lang.Object],
          classOf[java.lang.Object]
        )
      )
    } catch {
      case e: ReflectiveOperationException => null
    }
  private final val addOneMH: MethodHandle =
    try {
      val builderClass = Class.forName("scala.collection.immutable.HashMapBuilder")
      lookup.findVirtual(
        builderClass,
        "addOne",
        MethodType.methodType(
          builderClass,
          classOf[java.lang.Object],
          classOf[java.lang.Object],
        )
      )
    } catch {
      case e: ReflectiveOperationException => null
    }
}
