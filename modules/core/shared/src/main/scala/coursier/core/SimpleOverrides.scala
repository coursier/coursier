package coursier.core

import dataclass.data

import scala.collection.mutable

@data class SimpleOverrides(
  map: Map[DependencyManagement.Key, DependencyManagement.SplitValues]
) {

  override lazy val hashCode: Int = map.hashCode()

  def isEmpty: Boolean =
    map.isEmpty

  def filterOnValues(f: (
    DependencyManagement.Key,
    DependencyManagement.Values.ValuesCore,
    DependencyManagement.Values.ValuesMergeable
  ) => Boolean): SimpleOverrides = {
    var anyChange = false
    val newMap = map.flatMap {
      case (k, values) =>
        val newValuesMap = values.map.filter {
          case (c, v) =>
            f(k, c, v)
        }
        if (newValuesMap.size == values.map.size)
          Seq(k -> values)
        else {
          anyChange = true
          if (newValuesMap.isEmpty) Nil
          else Seq(k -> DependencyManagement.SplitValues(newValuesMap))
        }
    }
    if (anyChange) SimpleOverrides(newMap)
    else this
  }

  def mapOnValues(f: (
    DependencyManagement.Key,
    DependencyManagement.Values
  ) => DependencyManagement.Values): SimpleOverrides = {
    var anyChange = false
    val newMap = map.map {
      case (k, sv) =>
        val newSv = sv.mapOnValues(f(k, _))
        k -> {
          if (sv == newSv) sv
          else {
            anyChange = true
            newSv
          }
        }
    }
    if (anyChange) SimpleOverrides(newMap)
    else this
  }

  lazy val hasProperties =
    map.exists {
      case (k, v) =>
        k.hasProperties || v.hasProperties
    }

  def mapEntries(
    f: (
      DependencyManagement.Key,
      DependencyManagement.SplitValues
    ) => (DependencyManagement.Key, DependencyManagement.SplitValues)
  ): SimpleOverrides = {
    var anyChange    = false
    var anyKeyChange = false
    val newEntries = map.toSeq.map {
      case (k, v) =>
        val newKv = f(k, v)
        if ((!anyChange || !anyKeyChange) && k != newKv._1) {
          anyChange = true
          anyKeyChange = true
        }
        else if (!anyChange && v != newKv._2)
          anyChange = true
        newKv
    }
    if (anyKeyChange)
      SimpleOverrides(
        newEntries
          .groupBy(_._1)
          .map {
            case (k, l) =>
              val l0 = l.map(_._2)
              k -> l0.tail.foldLeft(l0.head)(_.add(_))
          }
      )
    else if (anyChange)
      SimpleOverrides(newEntries.toMap)
    else
      this
  }

  def values(key: DependencyManagement.Key): Set[DependencyManagement.Values] =
    map.get(key).map(_.list).getOrElse(Set.empty)

  final lazy val enforceGlobalStrictVersions: SimpleOverrides =
    mapOnValues { case (_, v) => v.withGlobal(true) }

  final lazy val global: SimpleOverrides =
    SimpleOverrides(
      map.flatMap {
        case (k, v) =>
          val filteredList = v.list.filter(_.global)
          if (filteredList.size == v.list.size)
            Some(k -> v)
          else if (filteredList.isEmpty)
            None
          else {
            val newMap = filteredList.map(_.coreMergeable).groupBy(_._1).map {
              case (core, pairs) =>
                core -> DependencyManagement.Values.ValuesMergeable.merge(
                  pairs.toSeq.map(_._2)
                )
            }
            Some(k -> DependencyManagement.SplitValues(newMap))
          }
      }
    )

  def serialNonCommutativeAddOverrides(other: SimpleOverrides): SimpleOverrides =
    SimpleOverrides.serialNonCommutativeAdd(this, other)

  // true if first "contains" second
  def contains(other: SimpleOverrides): Boolean =
    other.map.forall {
      case (k, v) =>
        map.get(k).exists(_.contains(v))
    }

  def repr: String =
    map
      .toSeq
      .sortBy(_._1.repr)
      .flatMap {
        case (k, sv) =>
          sv.list.toSeq
            .sortBy { v =>
              (v.config.value, v.versionConstraint.asString)
            }
            .map(v => s"    ${k.repr}: ${v.repr}")
      }
      .mkString("\n")
}

object SimpleOverrides {
  val empty = SimpleOverrides(Map.empty)

  def parallelCommutativeAdd(values: SimpleOverrides*): SimpleOverrides = {
    val values0 = values.filter(!_.isEmpty)
    if (values0.isEmpty)
      empty
    else if (values0.length == 1)
      values0.head
    else {
      val m = new mutable.HashMap[DependencyManagement.Key, DependencyManagement.SplitValues]
      for (s <- values0; (k, v) <- s.map)
        m(k) = m.get(k) match {
          case Some(v0) => v0.add(v) // FIXME Inefficent?
          case None     => v
        }
      SimpleOverrides(m.toMap)
    }
  }

  def serialNonCommutativeAdd(values: SimpleOverrides*): SimpleOverrides = {
    val values0 = values.filter(!_.isEmpty)
    if (values0.isEmpty) empty
    else if (values0.length == 1) values0.head
    else {
      val m = new mutable.HashMap[DependencyManagement.Key, DependencyManagement.SplitValues]

      for (s <- values0; (k, v) <- s.map)
        m(k) = m.get(k) match {
          case Some(v0) => v0.orElse(v)
          case None     => v
        }

      SimpleOverrides(m.toMap)
    }
  }

  def fromDependencies(
    deps: Seq[(Configuration, Dependency)]
  ): SimpleOverrides =
    serialNonCommutativeAdd(
      deps.map {
        case (config, dep) =>
          val (k, v) = DependencyManagement.entry(config, dep)
          SimpleOverrides(Map(k -> v.splitValues))
      }: _*
    )
}
