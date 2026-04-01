package coursier.core

import coursier.core.DependencyManagement.Values.ValuesMergeable
import dataclass.data

import scala.collection.mutable

@data class Overrides(map: Map[MinimizedExclusions, SimpleOverrides]) {
  assert(map.nonEmpty)

  override lazy val hashCode: Int = map.hashCode()

  def contains(key: DependencyManagement.Key): Boolean =
    map.values.exists(_.map.contains(key))

  def forModule(org: Organization, module: ModuleName): Option[Overrides] = {
    val remaining = map.filter {
      case (excl, _) =>
        excl(org, module)
    }
    if (remaining.isEmpty) None
    else Some(Overrides(remaining))
  }

  def values(key: DependencyManagement.Key): Set[DependencyManagement.Values] =
    map.valuesIterator.map(_.values(key)).reduceLeft(_ ++ _)

  lazy val hasProperties: Boolean =
    map.exists {
      case (excl, simple) =>
        excl.hasProperties || simple.hasProperties
    }

  def mapEntries(
    f: (
      DependencyManagement.Key,
      DependencyManagement.SplitValues
    ) => (DependencyManagement.Key, DependencyManagement.SplitValues)
  ): Overrides = {
    var changed = false
    val updatedMap = map.map {
      case (excl, simple) =>
        val newSimple = simple.mapEntries(f)
        if (!changed && (simple ne newSimple))
          changed = true
        excl -> newSimple
    }
    if (changed) Overrides(updatedMap)
    else this
  }

  def contains(other: Overrides): Boolean =
    other.map.forall {
      case (excl, m) =>
        map.get(excl).exists(_.contains(m)) ||
        map.exists {
          case (excl0, m0) =>
            excl0.subsetOf(excl) && m0.contains(m)
        }
    }

  def addExclusions(excl: MinimizedExclusions): Overrides =
    if (map.keysIterator.exists(!excl.subsetOf(_))) {
      val entries = map.toSeq.map {
        case (excl0, simple) =>
          excl0.join(excl) -> simple
      }
      val naiveMap = entries.toMap
      Overrides(
        if (naiveMap.size == map.size) naiveMap
        else
          entries
            .groupBy(_._1)
            .map {
              case (k, l) =>
                k -> SimpleOverrides.parallelCommutativeAdd(l.map(_._2): _*)
            }
      )
    }
    else
      this

  final lazy val global: Overrides =
    if (map.exists(t => t._2 != t._2.global))
      Overrides(
        map.map {
          case (excl, simple) =>
            excl -> simple.global
        }
      )
    else
      this

  final def repr: String =
    if (map.isEmpty) "Overrides: none"
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

      val allEntries
        : Seq[(MinimizedExclusions, DependencyManagement.Key, DependencyManagement.Values)] =
        map.toSeq
          .sortBy(_._1.repr)
          .flatMap { case (excl, keyMap) =>
            keyMap.map.toSeq.sortBy(_._1.repr).flatMap { case (key, valuesMap) =>
              valuesMap.list.toSeq
                .sortBy(v => (v.config.value, v.versionConstraint.asString))
                .map((excl, key, _))
            }
          }

      val sectionLines =
        allEntries.groupBy(_._1).toSeq.sortBy(_._1.repr).flatMap { case (excl, groupEntries) =>
          val sorted = groupEntries.sortBy(e => (e._2.repr, e._3.versionConstraint.asString))
          excl.repr.linesIterator.map("  " + _).toSeq ++
            Seq("    ---") ++
            sorted.map { case (_, key, v) => s"    ${key.repr}: ${valueRepr(v)}" }
        }
      "Overrides:\n" + sectionLines.mkString("\n")
    }

  def serialNonCommutativeAddOverride(
    key: DependencyManagement.Key,
    values: DependencyManagement.Values
  ): Overrides =
    Overrides(
      map.map {
        case (excl, simple) =>
          excl -> simple.serialNonCommutativeAddOverrides(
            SimpleOverrides(Map(key -> values.splitValues))
          )
      }
    )

  def serialNonCommutativeAddOverrides(overrides: SimpleOverrides): Overrides =
    Overrides(
      map.map {
        case (excl, simple) =>
          excl -> simple.serialNonCommutativeAddOverrides(overrides)
      }
    )

  def map(
    substituteProps0: String => String,
    substitutePropsForVersion: String => String
  ): Overrides = {
    val withValues = mapEntries { (key, splitValues) =>
      val newKey = key.map(substituteProps0)
      val newSplitValues = splitValues.mapOnValues(
        _.mapButVersion(substituteProps0).mapVersion(substitutePropsForVersion)
      )
      newKey -> newSplitValues
    }
    var anyChange = false
    val mapped = withValues.map.toSeq.map {
      case (excl, simple) =>
        val excl0 = excl.map(substituteProps0)
        if (excl != excl0) {
          anyChange = true
          excl0 -> simple
        }
        else
          excl -> simple
    }
    if (anyChange)
      Overrides.parallelCommutativeAdd(
        Overrides(Map(mapped.head)),
        mapped.tail.map(kv => Overrides(Map(kv))): _*
      )
    else
      withValues
  }

  def split: Iterable[Overrides] =
    map.toSeq.map {
      case kv =>
        Overrides(Map(kv))
    }

  def clearSimpleOverrides: Overrides =
    if (map.exists(!_._2.isEmpty))
      Overrides(
        MinimizedExclusions.removeSuperfluous(map.keySet).map(_ -> SimpleOverrides.empty).toMap
      )
    else
      this
}

object Overrides {

  private val empty0 = Overrides(Map(MinimizedExclusions.zero -> SimpleOverrides.empty))
  def empty: Overrides =
    empty0

  def parallelCommutativeAdd(first: Overrides, other: Overrides*): Overrides =
    if (other.isEmpty)
      // Also call MinimizedExclusions.removeSuperfluous here?
      first
    else {
      val rawAllOverrides = (first +: other).flatMap(_.map)
      assert(rawAllOverrides.nonEmpty)
      val b = new mutable.HashMap[MinimizedExclusions, ::[SimpleOverrides]]
      for ((excl, simpleOverrides) <- rawAllOverrides)
        b(excl) = ::(simpleOverrides, b.getOrElse(excl, Nil))
      val overridesMap = b.toMap.map {
        case (excl, l) =>
          excl -> SimpleOverrides.parallelCommutativeAdd(l: _*)
      }
      assert(overridesMap.nonEmpty)

      val index: Map[SimpleOverrides, Seq[MinimizedExclusions]] = overridesMap
        .groupBy(_._2)
        .map {
          case (simpleOverrides, exclList) =>
            assert(exclList.nonEmpty)
            val res = simpleOverrides -> MinimizedExclusions.removeSuperfluous(exclList.map(_._1))
            assert(res._2.nonEmpty)
            res
        }
      assert(index.nonEmpty)

      val finalOverridesMap =
        if (index.size == overridesMap.size)
          overridesMap
        else
          index.flatMap {
            case (simpleOverrides, exclList) =>
              exclList.map((_, simpleOverrides))
          }
      assert(finalOverridesMap.nonEmpty)

      Overrides(finalOverridesMap)
    }
}
