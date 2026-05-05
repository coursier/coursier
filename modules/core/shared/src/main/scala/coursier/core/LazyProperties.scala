package coursier.core

import scala.collection.mutable
import scala.collection.immutable
import scala.collection.compat._

/** A sequence of properties, with a mechanism to resolve interpolated references in values.
  *
  * Resolution is lazy.
  *
  * ```
  * <properties>
  *   <key1>value1</key1>
  *   <key2>value2</key2>
  *   <key3>${key1},${key2}</key2>
  * </properties>
  * ```
  *
  * For backwards compatibility, this implements `Seq[(String, String)`, so it can be used in
  * `coursier.core.Project#properties` without making breaking changes to that API. This sequence
  * contains the raw values.
  *
  * `toMap` returns an implementation of `scala.collection.immutable.Map`. Values returned from this
  * map _are_ interpolated, although this is performed lazily.
  *
  * The `LazyProperties.merge` combinators can be used to concatenate multiple properties sequences,
  * typically to merge properties from child/parent layers and to merge intrinsic properties (like
  * `project.version`).
  *
  * These concatenations are themselves lazy, can be chained, and share internal state to enhance
  * performance and reduce memory usage. This state includes an index (`Map[String, Entry`). Each
  * `Entry` caches the parsed representation of the value as a seqeunce of literals and property
  * references. (`ParsedSubstituteProps`)
  *
  * Utility methods for interpolating values into strings use `ParsedSubstituteProps` to avoid
  * re-parsing the same value multiple times.
  *
  * Many `String`-typed values in the core model can themselves contain property references. Where
  * possible, a class carrying such a field (`value`) should also carry a `lazy val` (`parsedValue`)
  * to cache the parsed representation of that field, and then use
  * `LazyProperties.fastApply(value, parsedValue, f)` in place of `f(value)` in `map` methods.
  * `fastApply` will detect if `f` is of type `LazyProperties.Substitution` and use that to avoid
  * re-parsing the value.
  *
  * If a breaking API change is ever planned, we can cleanup code by: a) wrap all such values in a
  * class that encapsulates the parsed representation, and use this instead of `String`, b) change
  * the type of `Project.properties` to something no longer be a `Seq`, and c) change all the
  * `map(f: String => String)` methods to accept a `map(f: Substitutor)`
  */
private[coursier] sealed abstract class LazyProperties
    extends AbstractSeq[(String, String)] {

  protected def resolvedMap: immutable.Map[String, String]

  override def toMap[K, V](
    implicit ev: ((String, String)) <:< (K, V)
  ): immutable.Map[K, V] =
    resolvedMap.asInstanceOf[immutable.Map[K, V]]
}

private[coursier] object LazyProperties {

  private case object Empty extends LazyProperties {
    override def iterator: Iterator[(String, String)] = Iterator.empty
    override def length: Int                          = 0
    override def isEmpty: Boolean                     = true

    override def apply(idx: Int): (String, String) =
      throw new IndexOutOfBoundsException(idx.toString)

    override protected val resolvedMap: immutable.Map[String, String] =
      immutable.Map.empty
  }

  def merge(
    properties: collection.Seq[(String, String)],
    extraProperties: collection.Seq[(String, String)]
  ): immutable.Seq[(String, String)] =
    merge(Seq(properties, extraProperties))

  def mergeLayerMaps(
    layers: collection.Seq[collection.Map[String, _]]
  ): immutable.Seq[(String, String)] =
    merge(layers.iterator.map(mapLayerToSeq).toVector)

  def merge(
    layers: collection.Seq[collection.Seq[(String, String)]]
  ): immutable.Seq[(String, String)] = {
    var totalCount = 0
    layers.foreach(l => totalCount += layerCount(l))
    if (totalCount == 0) Empty
    else {
      val arr    = new Array[PropertyLayer](totalCount)
      var offset = 0
      layers.foreach(l => offset = fillLayers(l, arr, offset))
      new ConcatProperties(arr)
    }
  }

  def merge(
    layer1: collection.Seq[(String, String)],
    layer2: PropertyLayer
  ): immutable.Seq[(String, String)] = {
    val count  = layerCount(layer1) + 1
    val arr    = new Array[PropertyLayer](count)
    val offset = fillLayers(layer1, arr, 0)
    arr(offset) = layer2
    new ConcatProperties(arr)
  }

  def filterKeysNotIn(
    properties: collection.Seq[(String, String)],
    excludedKeys: Set[String]
  ): immutable.Seq[(String, String)] = {
    val count = layerCount(properties)
    if (count == 0) Empty
    else if (excludedKeys.isEmpty) {
      val arr = new Array[PropertyLayer](count)
      fillLayers(properties, arr, 0)
      new ConcatProperties(arr)
    }
    else {
      val arr = new Array[PropertyLayer](count)
      fillLayers(properties, arr, 0)
      new FilteredProperties(arr, excludedKeys)
    }
  }

  def substitute(
    s: String,
    lookup: PropertyValueLookup,
    trim: Boolean = false
  ): String = {
    val props = PropertyExpr.parse(s)
    props.substitute(s, lookup, trim)
  }

  final class PropertyEntry(val value: String) {
    lazy val parsed: PropertyExpr =
      PropertyExpr.parse(value)
  }

  abstract class PropertyLayer {
    def length: Int
    def key(i: Int): String
    def value(i: Int): String
    def tuple(i: Int): (String, String)
    def getOrNull(k: String): PropertyEntry

    def iterator: IterableOnce[(String, String)] =
      Iterator.tabulate(length) { i =>
        tuple(i)
      }
  }
  final class SeqPropertyLayer(val values: immutable.Seq[(String, String)]) extends PropertyLayer {
    assert(!values.isInstanceOf[ConcatProperties])

    override def iterator: IterableOnce[(String, String)] = values.iterator

    private lazy val index: java.util.Map[String, PropertyEntry] = {
      val m = new java.util.HashMap[String, PropertyEntry](math.max(16, values.length))
      var i = 0
      while (i < values.length) {
        val key = values(i)._1
        m.put(key, new PropertyEntry(values(i)._2))
        i += 1
      }
      m
    }

    override def length: Int = values.length

    override def key(i: Int): String = values(i)._1

    override def value(i: Int): String = values(i)._2

    override def tuple(i: Int): (String, String) = values(i)

    override def getOrNull(k: String): PropertyEntry = index.get(k)

  }

  private def layerFromSeq(layer: collection.Seq[(String, String)]): PropertyLayer =
    new SeqPropertyLayer(layer.toVector)

  private def layerCount(properties: collection.Seq[(String, String)]): Int =
    properties match {
      case concat: ConcatProperties     => concat.layers.length
      case filtered: FilteredProperties => filtered.layers.length
      case Empty                        => 0
      case other if other.isEmpty       => 0
      case _                            => 1
    }

  private def fillLayers(
    properties: collection.Seq[(String, String)],
    dest: Array[PropertyLayer],
    offset: Int
  ): Int =
    properties match {
      case concat: ConcatProperties =>
        System.arraycopy(concat.layers, 0, dest, offset, concat.layers.length)
        offset + concat.layers.length
      case filtered: FilteredProperties =>
        System.arraycopy(filtered.layers, 0, dest, offset, filtered.layers.length)
        offset + filtered.layers.length
      case other if other.isEmpty =>
        offset
      case other =>
        dest(offset) = layerFromSeq(other)
        offset + 1
    }

  private def mapLayerToSeq(layer: collection.Map[String, _]): immutable.Seq[(String, String)] = {
    val b = Vector.newBuilder[(String, String)]

    layer.iterator.foreach {
      case (k, s: String) =>
        b += ((k, s))
      case (k, values: collection.Seq[_]) =>
        values.foreach {
          case s: String => b += ((k, s))
          case other =>
            throw new IllegalArgumentException(
              s"Property value sequences must contain only strings, got ${other.getClass.getName}"
            )
        }
      case (k, values: Iterable[_]) =>
        values.foreach {
          case s: String => b += ((k, s))
          case other =>
            throw new IllegalArgumentException(
              s"Property value collections must contain only strings, got ${other.getClass.getName}"
            )
        }
      case (_, other) =>
        throw new IllegalArgumentException(
          s"Property map values must be a String or a Seq[String], got ${other.getClass.getName}"
        )
    }

    b.result()
  }

  private abstract class LayeredLazyProperties(
    val layers: Array[PropertyLayer]
  ) extends LazyProperties {

    override def length: Int = {
      var total = 0
      var i     = 0
      while (i < layers.length) {
        total += layers(i).length
        i += 1
      }
      total
    }

    final def pairAtGlobalIndex(idx: Int): (String, String) = {
      if (idx < 0)
        throw new IndexOutOfBoundsException(idx.toString)

      var remaining = idx
      var layerIdx  = 0
      while (layerIdx < layers.length) {
        val layerSize = layers(layerIdx).length
        if (remaining < layerSize)
          return layers(layerIdx).tuple(remaining)
        remaining -= layerSize
        layerIdx += 1
      }

      throw new IndexOutOfBoundsException(idx.toString)
    }

    final def winnerEntryForKeyOrNull(key: String): PropertyEntry = {
      var layerIdx = layers.length - 1
      while (layerIdx >= 0) {
        val entry = layers(layerIdx).getOrNull(key)
        if (entry != null)
          return entry
        layerIdx -= 1
      }
      null
    }
  }

  private final class LazyMapWithSubstitutedValues(
    owner: LayeredLazyProperties,
    includeKey: String => Boolean
  ) extends AbstractMap[String, String] with PropertyValueLookup {
    override def lookupOrNull(key: String): PropertyExpr =
      owner.winnerEntryForKeyOrNull(key) match {
        case null => null
        case entry =>
          entry.parsed
      }

    private[this] lazy val entries0 = {
      val b        = Vector.newBuilder[(String, String)]
      val seen     = mutable.HashSet.empty[String]
      var layerIdx = owner.layers.length - 1

      while (layerIdx >= 0) {
        val layer = owner.layers(layerIdx)
        var idx   = layer.length - 1

        while (idx >= 0) {
          val k = layer.key(idx)
          if (includeKey(k) && !seen(k)) {
            seen += k
            val v = layer.value(idx)
            b += ((k, v))
          }
          idx -= 1
        }

        layerIdx -= 1
      }

      b.result().reverse
    }

    override def getOrElse[V1 >: String](key: String, default: => V1): V1 =
      if (!includeKey(key)) default
      else
        owner.winnerEntryForKeyOrNull(key) match {
          case null => default
          case entry =>
            entry.parsed.substitute(entry.value, this, false)
        }

    override def get(key: String): Option[String] =
      if (!includeKey(key)) None
      else
        owner.winnerEntryForKeyOrNull(key) match {
          case null => None
          case entry =>
            Some(entry.parsed.substitute(entry.value, this, false))
        }

    override def iterator: Iterator[(String, String)] =
      entries0.iterator

    override def removed(key: String): immutable.Map[String, String] =
      immutable.Map.from(entries0) - key

    override def updated[V1 >: String](
      key: String,
      value: V1
    ): immutable.Map[String, V1] =
      immutable.Map.from(entries0.map { case (k, v) => (k, v: V1) }).updated(key, value)
  }

  private final class ConcatProperties(
    layers: Array[PropertyLayer]
  ) extends LayeredLazyProperties(layers) {

    override def iterator: Iterator[(String, String)] =
      layers.iterator.flatMap(_.iterator)

    override def apply(idx: Int): (String, String) =
      pairAtGlobalIndex(idx)

    override protected lazy val resolvedMap: immutable.Map[String, String] =
      new LazyMapWithSubstitutedValues(this, _ => true)
  }

  private final class FilteredProperties(
    layers: Array[PropertyLayer],
    excludedKeys: Set[String]
  ) extends LayeredLazyProperties(layers) {

    private[this] lazy val filteredLength = {
      var count    = 0
      var layerIdx = 0
      while (layerIdx < layers.length) {
        val layer = layers(layerIdx)
        var idx   = 0
        while (idx < layer.length) {
          if (!excludedKeys.contains(layer.key(idx)))
            count += 1
          idx += 1
        }
        layerIdx += 1
      }
      count
    }

    override def iterator: Iterator[(String, String)] =
      layers.iterator.flatMap(_.iterator).filter { case (k, _) => !excludedKeys.contains(k) }

    override def length: Int =
      filteredLength

    override def apply(idx: Int): (String, String) = {
      if (idx < 0)
        throw new IndexOutOfBoundsException(idx.toString)

      var remaining = idx
      var layerIdx  = 0
      while (layerIdx < layers.length) {
        val layer = layers(layerIdx)
        var i     = 0
        while (i < layer.length) {
          val k = layer.key(i)
          if (!excludedKeys.contains(k)) {
            if (remaining == 0)
              return (k, layer.value(i))
            remaining -= 1
          }
          i += 1
        }
        layerIdx += 1
      }

      throw new IndexOutOfBoundsException(idx.toString)
    }

    override protected lazy val resolvedMap: immutable.Map[String, String] =
      new LazyMapWithSubstitutedValues(this, k => !excludedKeys.contains(k))
  }
}
