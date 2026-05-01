package coursier.util

import java.lang.invoke.{MethodHandle, MethodHandles, MethodType}
import scala.collection.immutable.HashMap
import scala.collection.compat._

private[coursier] object HashMapBuilderFactory {
  def apply[A, B <: AnyRef]: HashMapBuilder[A, B] =
    if (getOrElseMH == null || addOneMH == null) new CompatibleHashMapBuilder[A, B]
    else new FastHashMapBuilder[A, B]
  // Uses non-public API of scala.collection.immutable.HashMapBuilder
  // to allow lookup of values mid-build and to enable tuple-free addOne.
  final class FastHashMapBuilder[A, B <: AnyRef] extends HashMapBuilder[A, B] {
    import HashMapBuilder.toGrowableExtensionMethods
    lazy val delegate = HashMap.newBuilder[A, B]

    // TODO SCALA_213_BASELINE Uncomment when Scala 2.13 becomes the baseline
    // override def knownSize: Int = delegate.knownSize

    def clear(): Unit = delegate.clear()

    def result(): HashMap[A, B] = delegate.result()

    def getOrNull(a: A): B =
      getOrElseMH.invoke(delegate, a, null).asInstanceOf[B]

    override def add(a: A, b: B): FastHashMapBuilder.this.type = {
      addOneMH.invoke(delegate, a, b)
      this
    }

    def addOne(elem: (A, B)): FastHashMapBuilder.this.type = {
      delegate.addOne(elem)
      this
    }

    def addAll(elems: IterableOnce[(A, B)]): FastHashMapBuilder.this.type = {
      delegate.addAll(elems)
      this
    }
  }

  private val lookup = MethodHandles.lookup()
  private final val getOrElseMH: MethodHandle =
    try
      lookup.findVirtual(
        Class.forName("scala.collection.immutable.HashMapBuilder"),
        "getOrElse",
        MethodType.methodType(
          classOf[java.lang.Object],
          classOf[java.lang.Object],
          classOf[java.lang.Object]
        )
      )
    catch {
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
          classOf[java.lang.Object]
        )
      )
    }
    catch {
      case e: ReflectiveOperationException => null
    }
}
