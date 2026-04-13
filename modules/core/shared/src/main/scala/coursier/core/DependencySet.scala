package coursier.core

import scala.collection.compat._
import scala.collection.mutable
import scala.collection.immutable.IntMap

final class DependencySet private (
  val set: Set[Dependency],
  grouped: Map[
    DependencySet.DepNoExclusions,
    DependencySet.Sets[Dependency]
  ]
) extends Product {

  import DependencySet._

  def canEqual(that: Any): Boolean =
    that.isInstanceOf[DependencySet]

  def productArity: Int = 1
  //   Add that back if / when dropping Scala 2.12 support
  // override def productElementName(n: Int): String =
  //   if (n == 0) "set"
  //   else throw new NoSuchElementException(s"Element at index $n in DependencySet")
  def productElement(n: Int): Any =
    if (n == 0) set
    else throw new NoSuchElementException(s"Element at index $n in DependencySet")

  override def equals(obj: Any): Boolean =
    obj match {
      case other: DependencySet =>
        set == other.set
      case _ => false
    }

  override lazy val hashCode: Int = {
    var code = 17 + "coursier.core.DependencySet".##
    code = 37 * code + set.##
    37 * code
  }

  override def toString: String =
    s"DependencySet($set, ${grouped.size} groups)"

//  assert(grouped.iterator.map(_._2.size).sum == set.size, s"${grouped.iterator.map(_._2.size).sum} != ${set.size}")
//  assert(grouped.forall { case (dep, l) => l.forall(_.moduleVersion == dep.moduleVersion) })

  private def aggregateDep0(dep: Dependency, children: Set[Dependency]): Dependency =
    dep.withOverridesMap(
      Overrides.parallelCommutativeAdd(dep.overridesMap, children.toSeq.map(_.overridesMap): _*)
    )

  private def aggregateDep(sets: Sets[Dependency]): Iterator[Dependency] =
    sets.children
      .iterator
      .map {
        case (k, v) =>
          aggregateDep0(k, v)
      }

  lazy val minimizedSet: Set[Dependency] =
    grouped.iterator
      .map(_._2)
      .flatMap(aggregateDep)
      .toSet

  private def emptyDepSets: Sets[Dependency] =
    Sets.empty[Dependency](
      dep => {
        assert(dep.overridesMap.map.size == 1)
        dep.overridesMap.map.head._1.size()
      },
      (a, b) => {
        assert(a.overridesMap.map.size == 1)
        assert(b.overridesMap.map.size == 1)
        a.overridesMap.map.head._1.subsetOf(b.overridesMap.map.head._1)
      }
    )

  def contains(dependency: Dependency): Boolean =
    DependencySet.DepNoExclusions.create(dependency).forall { dep0 =>
      grouped
        .getOrElse(dep0, emptyDepSets)
        .contains(dependency)
    }

  def covers(dependency: Dependency, debug: Boolean = false): Boolean =
    DependencySet.DepNoExclusions.create(dependency).forall { dep0 =>
      val set = grouped.getOrElse(dep0, emptyDepSets)
      set.covers0(dependency).exists { covering =>
        val baseMap = aggregateDep0(covering, set.children(covering)).overridesMap
        val res     = baseMap.contains(dependency.overridesMap)
        if (debug)
          System.err.println(
            s"""baseMap
               |
               |$baseMap
               |
               |dependency.overridesMap
               |
               |${dependency.overridesMap}
               |
               |-> $res
               |""".stripMargin
          )
        res
      }
    }

  def add(dependency: Dependency): DependencySet =
    add(Seq(dependency))

  def add(dependency: Iterable[Dependency]): DependencySet =
    addNoCheck(dependency.filter(!set(_)))

  private def addNoCheck(dependencies: Iterable[Dependency]): DependencySet =
    if (dependencies.isEmpty)
      this
    else {
      var m = grouped
      for {
        dep  <- dependencies.flatMap(_.split)
        dep0 <- DependencySet.DepNoExclusions.create(dep)
      }
        // Optimized map value addition. Only mutate map if we made a change
        m.get(dep0) match {
          case None =>
            m = m + (dep0 -> emptyDepSets.add(dep))
          case Some(groupSet) =>
            val groupSet0 = groupSet.add(dep)
            if (groupSet ne groupSet0)
              m = m + (dep0 -> groupSet0)
        }
      new DependencySet(set ++ dependencies, m)
    }

  private def removeNoCheck(dependencies: Iterable[Dependency]): DependencySet =
    if (dependencies.isEmpty)
      this
    else {
      var m = grouped
      for {
        dep  <- dependencies
        dep0 <- DependencySet.DepNoExclusions.create(dep)
      } {
        // getOrElse useful if we're passed duplicated stuff in dependencies
        val prev = m.getOrElse(dep0, emptyDepSets)
        if (prev.contains(dep))
          if (prev.size0 <= 1)
            m -= dep0
          else
            m += (dep0 -> prev.remove(dep))
      }

      new DependencySet(set -- dependencies, m.toMap)
    }

  def setValues(newSet: Set[Dependency]): DependencySet = {
    val toAdd    = newSet -- set
    val toRemove = set -- newSet
    addNoCheck(toAdd)
      .removeNoCheck(toRemove)
  }

  private[coursier] def containsModule(mod: Module): Boolean =
    grouped.exists(_._1.dep.module == mod)

}

object DependencySet {
  val empty = new DependencySet(Set.empty, Map.empty)

  private object Sets {
    def empty[T](size: T => Int, subsetOf: (T, T) => Boolean): Sets[T] =
      Sets(IntMap.empty, Map.empty, Map.empty, size, subsetOf)
  }

  private final case class DepNoExclusions private (dep: Dependency) {
    assert(dep.overridesMap.map.size == 1)
    assert(dep.overridesMap.map.head._1.isEmpty)
  }

  private object DepNoExclusions {
    def create(dep: Dependency): Seq[DepNoExclusions] =
      dep.overridesMap.map.toSeq.map {
        case (_, simpleOverrides) =>
          DepNoExclusions(
            dep.withOverridesMap(Overrides.empty.serialNonCommutativeAddOverrides(simpleOverrides))
          )
      }
  }

  /** A set for elements that have a partial ordering and where if a <= b, then we can discard a
    * from the set
    *
    * In more detail, we keep track of all elements (w/e the partial order), and we also keep track
    * of the partial orderings we found out about. `children.keySet ++ parents.keySet` has all the
    * elements, while just `children.keySet` takes the order into account (all elements in
    * `parents.keySet` are <= to at least one element in `children.keySet`).
    *
    * In practice in coursier, the element type is Dependency, and we discard dependencies that
    * bring transitive dependencies that are all going to be brought by a broader dependency, like
    * two dependencies for the same module and version, but one with some exclusions and the other
    * without exclusions. The dependency with exclusions can only bring elements that the dependency
    * without exclusions will bring, so we can effectively discard the dependency with exclusions.
    *
    * Invariant: all elements in the `required` values are keys in `children`, and the other way
    * around too
    *
    * assert(required.valuesIterator.foldLeft(Set.empty[T])(_ ++ _) == children.keySet)
    *
    * Invariant: elements in required can only be associated to one key
    *
    * assert(required.valuesIterator.map(_.size).sum == children.size)
    *
    * Invariant: all values in parents
    *
    * @param required
    *   prefiltering for the partial ordering. If an element has size n, we assume only elements
    *   that have a key <= n to be possibly >= to the former element.
    * @param children
    * @param parents
    * @param size
    *   size for the prefiltering of partial ordering (see `required`)
    * @param subsetOf
    *   whether the first passed element is >= to the second one
    */
  private final case class Sets[T] private (
    required: IntMap[Set[T]],
    children: Map[T, Set[T]],
    parents: Map[T, T],
    size: T => Int,
    subsetOf: (T, T) => Boolean
  ) {

    def iterator: Iterator[T] =
      children.keysIterator ++ parents.keysIterator

    assert(required.valuesIterator.foldLeft(Set.empty[T])(_ ++ _) == children.keySet)
    assert(
      required.valuesIterator.map(_.size).sum == children.size, {
        val set1 = required.valuesIterator.foldLeft(Set.empty[T])(_ ++ _)
        val set2 = children.keySet
        s"""required.valuesIterator.map(_.size).sum = ${required.valuesIterator.map(_.size).sum}
           |children.size = ${children.size}
           |set1 -- set2 = ${set1 -- set2}
           |set2 -- set1 = ${set2 -- set1}
           |""".stripMargin
      }
    )

    assert(
      parents.valuesIterator.forall(children.contains),
      s"""Missing in children: ${parents.valuesIterator.filter(!children.contains(_))}
         |""".stripMargin
    )

    /** How many elements were added to this set
      *
      * This *includes* elements that are <= to some others, and that can be discarded in practice
      */
    def size0: Int = parents.size + children.size

    /** Checks whether a predicate is true for all elements
      *
      * The check is also run against elements that are <= to some others
      */
    def forall(f: T => Boolean): Boolean =
      (parents.keysIterator ++ children.keysIterator)
        .forall(f)

    /** Checks whether an element belong to this set
      *
      * The check includes elements that are <= to some others
      */
    def contains(t: T): Boolean =
      parents.contains(t) || children.contains(t)

    /** Whether this set contains the passed element or contains elements that are >= to it
      */
    def covers(t: T): Boolean =
      contains(t) || {
        val n = size(t)
        required.view.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).exists {
          s0 => subsetOf(s0, t)
        }
      }

    def covers0(t: T): Option[T] =
      if (children.contains(t)) Some(t)
      else
        parents.get(t).orElse {
          val n = size(t)
          required.view.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).find {
            s0 => subsetOf(s0, t)
          }
        }

    private def forceAddIn(
      s: T,
      required: IntMap[Set[T]],
      children: Map[T, Set[T]],
      parents: Map[T, T]
    ): (IntMap[Set[T]], Map[T, Set[T]], Map[T, T]) = {

      val n = size(s)

      // Element that s is <= to
      val subsetOpt = required.view.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).find {
        s0 => subsetOf(s0, s)
      }

      subsetOpt match {
        case None =>
          val higherThan = required.view.filterKeys(_ >= n).iterator
            .flatMap {
              case (n0, set) =>
                set.iterator.map((n0, _))
            }
            .filter {
              case (n0, elem0) =>
                subsetOf(s, elem0)
            }
            .toSeq
          if (higherThan.isEmpty) {
            val required0 = required + (n -> (required.getOrElse(n, Set.empty) + s))
            val children0 = children + (s -> Set.empty[T])
            (required0, children0, parents)
          }
          else {
            val cleanedUpRequired = higherThan.foldLeft(required) {
              case (acc, (n0, elem)) =>
                val updatedSet = acc(n0) - elem
                if (updatedSet.isEmpty)
                  acc - n0
                else
                  acc + (n0 -> updatedSet)
            }
            val allChildren = {
              val set = new mutable.HashSet[T]
              for ((_, elem) <- higherThan)
                set ++= children(elem)
              set.toSet
            }
            val cleanedUpChildren = children -- higherThan.map(_._2)
            val required0      =
              cleanedUpRequired + (n -> (cleanedUpRequired.getOrElse(n, Set.empty) + s))
            val children0      = cleanedUpChildren + (s -> allChildren)
            val updatedParents = parents ++ allChildren.iterator.map(_ -> s)
            (required0, children0, updatedParents)
          }
        case Some(subset) =>
          val children0 = children + (subset -> (children.getOrElse(subset, Set.empty) + s))
          val parents0  = parents + (s       -> subset)
          (required, children0, parents0)
      }
    }

    // If called, s must not be an existing key in children, nor in parents
    private def forceAdd(s: T): Sets[T] = {
      val (newRequired, newChildren, newParents) = forceAddIn(s, required, children, parents)
      Sets(newRequired, newChildren, newParents, size, subsetOf)
    }

    /** Adds an element to this set
      */
    def add(s: T): Sets[T] =
      if (children.contains(s) || parents.contains(s))
        this
      else
        forceAdd(s)

    /** Removes an element from this set
      */
    def remove(s: T): Sets[T] =
      children.get(s) match {
        case Some(children0) =>
          val required0 = {
            val size0 = size(s)
            val elem  = required(size0) - s
            assert(elem.size < required(size0).size)
            if (elem.isEmpty)
              required - size0
            else
              required + (size0 -> elem)
          }
          val parents0 = parents -- children0
          val (r0, c0, p0) = children0.foldLeft((required0, children - s, parents0)) {
            case ((r, c, p), elem) =>
              forceAddIn(elem, r, c, p)
          }
          Sets(r0, c0, p0, size, subsetOf)
        case None =>
          parents.get(s) match {
            case Some(parent) =>
              Sets(
                required,
                children + (parent -> (children(parent) - s)),
                parents - s,
                size,
                subsetOf
              )
            case None =>
              this
          }
      }
  }
}
