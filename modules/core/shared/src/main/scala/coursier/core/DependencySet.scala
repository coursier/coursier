package coursier.core

import coursier.core.DependencySet.Sets

import scala.collection.compat._
import scala.collection.immutable.IntMap
import scala.collection.mutable

final class DependencySet private (
  val set: Set[Dependency],
  grouped: Map[Dependency, Sets[Dependency]]
) {

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

  lazy val minimizedSet: Set[Dependency] =
    grouped.iterator.flatMap(_._2.children.keysIterator).toSet

  def contains(dependency: Dependency): Boolean = {
    val dep0 = dependency.clearExclusions
    val set  = grouped.getOrElse(dep0, Sets.empty[Dependency])
    set.contains(dependency)
  }

  def covers(dependency: Dependency): Boolean = {
    val dep0 = dependency.clearExclusions
    val set  = grouped.getOrElse(dep0, Sets.empty[Dependency])
    set.covers(
      dependency,
      _.minimizedExclusions.size,
      (a, b) => a.minimizedExclusions.subsetOf(b.minimizedExclusions)
    )
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
      for (dep <- dependencies) {
        val dep0 = dep.clearExclusions
        // Optimized map value addition. Only mutate map if we made a change
        m.get(dep0) match {
          case None =>
            m = m + (dep0 -> Sets.empty[Dependency].add(
              dep,
              _.minimizedExclusions.size,
              (a, b) => a.minimizedExclusions.subsetOf(b.minimizedExclusions)
            ))
          case Some(groupSet) =>
            val groupSet0 = groupSet.add(
              dep,
              _.minimizedExclusions.size,
              (a, b) => a.minimizedExclusions.subsetOf(b.minimizedExclusions)
            )
            if (groupSet ne groupSet0)
              m = m + (dep0 -> groupSet0)
        }
      }
      new DependencySet(set ++ dependencies, m)
    }

  def remove(dependencies: Iterable[Dependency]): DependencySet =
    removeNoCheck(dependencies.filter(set))

  private def removeNoCheck(dependencies: Iterable[Dependency]): DependencySet =
    if (dependencies.isEmpty)
      this
    else {
      var m = grouped
      for (dep <- dependencies) {
        val dep0 = dep.clearExclusions
        // getOrElse useful if we're passed duplicated stuff in dependencies
        val prev = m.getOrElse(dep0, Sets.empty)
        if (prev.contains(dep))
          if (prev.size <= 1)
            m -= dep0
          else {
            val l = prev
              .remove(
                dep,
                _.minimizedExclusions.size,
                (a, b) => a.minimizedExclusions.subsetOf(b.minimizedExclusions)
              )
            m += ((dep0, l))
          }
      }

      new DependencySet(set -- dependencies, m.toMap)
    }

  def setValues(newSet: Set[Dependency]): DependencySet = {
    val toAdd    = newSet -- set
    val toRemove = set -- newSet
    addNoCheck(toAdd)
      .removeNoCheck(toRemove)
  }

}

object DependencySet {
  val empty = new DependencySet(Set.empty, Map.empty)

  private object Sets {
    def empty[T]: Sets[T] = Sets(IntMap.empty, Map.empty, Map.empty)
  }

  private final case class Sets[T] private (
    required: IntMap[Set[T]],
    children: Map[T, Set[T]],
    parents: Map[T, T]
  ) {

    def size: Int = parents.size + children.size

    def forall(f: T => Boolean): Boolean =
      (parents.keysIterator ++ children.keysIterator)
        .forall(f)

    def contains(t: T): Boolean =
      parents.contains(t) || children.contains(t)

    def covers(t: T, size: T => Int, subsetOf: (T, T) => Boolean): Boolean =
      contains(t) || {
        val n = size(t)
        required.view.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).exists {
          s0 => subsetOf(s0, t)
        }
      }

    private def forceAdd(s: T, size: T => Int, subsetOf: (T, T) => Boolean): Sets[T] = {

      val n = size(s)

      val subsetOpt = required.view.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).find {
        s0 => subsetOf(s0, s)
      }

      subsetOpt match {
        case None =>
          val required0 = required + (n -> (required.getOrElse(n, Set.empty) + s))
          val children0 = children + (s -> Set.empty[T])
          Sets(required0, children0, parents)
        case Some(subset) =>
          val children0 = children + (subset -> (children.getOrElse(subset, Set.empty) + s))
          val parents0  = parents + (s       -> subset)
          Sets(required, children0, parents0)
      }
    }

    def add(s: T, size: T => Int, subsetOf: (T, T) => Boolean): Sets[T] =
      if (children.contains(s) || parents.contains(s))
        this
      else
        forceAdd(s, size, subsetOf)

    def remove(s: T, size: T => Int, subsetOf: (T, T) => Boolean): Sets[T] =
      children.get(s) match {
        case Some(children0) =>
          val required0 = {
            val elem = required.getOrElse(size(s), Set.empty) - s
            if (elem.isEmpty)
              required - size(s)
            else
              required + (size(s) -> elem)
          }
          val parents0 = parents -- children0
          val sets0    = Sets(required0, children - s, parents0)
          children0.foldLeft(sets0)(_.forceAdd(_, size, subsetOf))
        case None =>
          parents.get(s) match {
            case Some(parent) =>
              Sets(
                required,
                children + (parent -> (children.getOrElse(parent, Set.empty) - s)),
                parents - s
              )
            case None =>
              this
          }
      }
  }
}
