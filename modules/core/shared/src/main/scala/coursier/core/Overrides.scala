package coursier.core

import java.util.concurrent.ConcurrentMap
import scala.util.control.compat.ControlThrowable

sealed abstract class Overrides extends Product with Serializable {
  private val cache: ConcurrentMap[Any, Any] =
    new java.util.concurrent.ConcurrentHashMap[Any, Any]()
  private[core] def cached[T](key: Any)(f: => T): T =
    cache.computeIfAbsent(
      key,
      _ =>
        f
    ).asInstanceOf[T]

  def get(key: DependencyManagement.Key): Option[DependencyManagement.Values]
  def contains(key: DependencyManagement.Key): Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty

  @deprecated("Use map instead")
  def maps: Seq[DependencyManagement.GenericMap] = Seq(map)
  def map: DependencyManagement.GenericMap
  def flatten: DependencyManagement.GenericMap =
    map

  def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides
  def map(
    f: (
      DependencyManagement.Key,
      DependencyManagement.Values
    ) => (DependencyManagement.Key, DependencyManagement.Values)
  ): Overrides
  def transform(f: (
    DependencyManagement.Key,
    DependencyManagement.Values
  ) => DependencyManagement.Values): Overrides
  def mapMap(
    f: DependencyManagement.GenericMap => Option[DependencyManagement.GenericMap]
  ): Overrides

  def hasProperties: Boolean
  def globalCount: Option[Int]

  final lazy val enforceGlobalStrictVersions: Overrides =
    map { (k, v) =>
      (k, v.withGlobal(true))
    }

  final def repr: String =
    if (isEmpty) "Overrides: none"
    else {
      def valueRepr(v: DependencyManagement.Values): String = {
        val parts = Seq.newBuilder[String]
        parts += v.versionConstraint.asString
        if (v.config.value.nonEmpty) parts += s"config:${v.config.value}"
        if (v.optional) parts += "optional"
        if (v.global) parts += "global"
        val exclusionsPart =
          if (v.minimizedExclusions.isEmpty)
            ""
          else
            "\n" + v.minimizedExclusions.repr.linesWithSeparators.map("    " + _).mkString
        parts.result().mkString(" ") + exclusionsPart
      }

      val groupEntries: Seq[(DependencyManagement.Key, DependencyManagement.Values)] =
        flatten.toSeq.sortBy(e => (e._1.repr, e._2.versionConstraint.asString))

      val sectionLines = groupEntries.map {
        case (key, v) =>
          s"    ${key.repr}: ${valueRepr(v)}"
      }
      "Overrides:\n" + sectionLines.mkString("\n")
    }
}

object Overrides {
  private val Found = new ControlThrowable() {}

  private final case class Impl(
    map: DependencyManagement.GenericMap,
    override val globalCount: Option[Int] = None
  ) extends Overrides {

    override lazy val hashCode: Int = map.hashCode()

    def get(key: DependencyManagement.Key): Option[DependencyManagement.Values] =
      map.get(key)
    def contains(key: DependencyManagement.Key): Boolean =
      map.contains(key)
    lazy val isEmpty: Boolean =
      map.forall(_._2.isEmpty)

    def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides = {
      val updatedMap = map.filter {
        case (k, v) =>
          f(k, v)
      }
      if (map.size == updatedMap.size) this
      else Overrides(updatedMap)
    }
    def map(
      f: (
        DependencyManagement.Key,
        DependencyManagement.Values
      ) => (DependencyManagement.Key, DependencyManagement.Values)
    ): Overrides = {
      var changed     = false
      var globalCount = 0
      val updatedMap = map.map {
        case kv @ (k, v) =>
          // FIXME Key collisions after applying f?
          val updated = f(k, v)
          if (!changed && kv != updated)
            changed = true
          if (updated._2.global) globalCount += 1
          updated
      }
      if (changed) Overrides.Impl(updatedMap, Some(globalCount))
      else this
    }
    def transform(f: (
      DependencyManagement.Key,
      DependencyManagement.Values
    ) => DependencyManagement.Values): Overrides =
      map match {
        case immMap: scala.collection.immutable.Map[
              DependencyManagement.Key,
              DependencyManagement.Values
            ] =>
          var globalCount = 0
          val transformed = immMap.transform { (k, v) =>
            val newV = f(k, v); if (newV.global) globalCount += 1; newV
          }
          if (transformed eq map) this
          else Overrides.Impl(transformed, Some(globalCount))
        case _ =>
          map((k, v) => (k, f(k, v)))
      }
    lazy val hasProperties =
      try {
        map.foreachEntry((k, v) =>
          if (
            k.organization.value.contains("$") ||
            k.name.value.contains("$") ||
            k.classifier.value.contains("$") ||
            k.`type`.value.contains("$") ||
            v.config.value.contains("$") ||
            v.versionConstraint.asString.contains("$") ||
            v.minimizedExclusions.hasProperties
          ) throw Found
        )
        false
      }
      catch {
        case Found => true
      }
    def mapMap(
      f: DependencyManagement.GenericMap => Option[DependencyManagement.GenericMap]
    ): Overrides =
      f(map)
        .map(Overrides(_))
        .getOrElse(this)
  }

  private val empty0 = Impl(Map.empty, Some(0))
  def empty: Overrides =
    empty0

  def apply(map: DependencyManagement.GenericMap): Overrides = {
    val emptyCount = map.valuesIterator.count(_.isEmpty)
    if (emptyCount == map.size) empty
    else if (emptyCount == 0) Impl(map.toMap)
    else Impl(map.filter(!_._2.isEmpty).toMap)
  }

  def add(overrides: Overrides): Overrides =
    if (overrides.isEmpty) empty0
    else overrides
  def add(overrides1: Overrides, overrides2: Overrides): Overrides = {
    val overrides1IsEmpty = overrides1.isEmpty
    val overrides2IsEmpty = overrides2.isEmpty
    (overrides1IsEmpty, overrides2IsEmpty) match {
      case (true, true)  => empty0
      case (true, false) => overrides2
      case (false, true) => overrides1
      case _ =>
        val temp =
          DependencyManagement.addAll(
            Map.empty,
            Seq(overrides1.map, overrides2.map)
          )
        if (temp.map == overrides1.map)
          overrides1
        else if (temp.map == overrides2.map)
          overrides2
        else
          Overrides.Impl(temp.map, Some(temp.globalCount))
    }
  }
  def add(overrides: Overrides*): Overrides =
    overrides.filter(_.nonEmpty) match {
      case Seq()     => empty
      case Seq(elem) => elem
      case more =>
        val addAllResult = DependencyManagement.addAll(
          Map.empty[DependencyManagement.Key, DependencyManagement.Values],
          more.map(_.map)
        )
        Overrides.Impl(addAllResult.map, Some(addAllResult.globalCount))
    }
}
