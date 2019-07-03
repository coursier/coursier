package coursier.core

import coursier.core.DependencySet.Sets

import scala.collection.immutable.TreeMap
import scala.collection.mutable

final case class DependencySet(
  set: Set[Dependency],
  grouped: Map[Dependency, Sets[Dependency]]
) {

  assert(grouped.iterator.map(_._2.size).sum == set.size, s"${grouped.iterator.map(_._2.size).sum} != ${set.size}")
  assert(grouped.forall { case (dep, l) => l.forall(_.moduleVersion == dep.moduleVersion) })

  lazy val minimizedSet: Set[Dependency] =
    grouped.iterator.flatMap(_._2.children.keysIterator).toSet

  def add(dependency: Dependency): DependencySet =
    add(Seq(dependency))

  def add(dependency: Iterable[Dependency]): DependencySet =
    addNoCheck(dependency.filter(!set(_)))

  private def addNoCheck(dependencies: Iterable[Dependency]): DependencySet =
    if (dependencies.isEmpty)
      this
    else {
      val m = new mutable.HashMap[Dependency, Sets[Dependency]]
      m ++= grouped
      for (dep <- dependencies) {
        val dep0 = dep.clearExclusions
        val l = m.getOrElse(dep0, Sets.empty[Dependency]).add(dep, _.exclusions.size, (a, b) => a.exclusions.subsetOf(b.exclusions))
        m(dep0) = l
      }
      DependencySet(set ++ dependencies, m.toMap)
    }

  def remove(dependencies: Iterable[Dependency]): DependencySet =
    removeNoCheck(dependencies.filter(set))

  private def removeNoCheck(dependencies: Iterable[Dependency]): DependencySet =
    if (dependencies.isEmpty)
      this
    else {
      val m = new mutable.HashMap[Dependency, Sets[Dependency]]
      m ++= grouped
      for (dep <- dependencies) {
        val dep0 = dep.clearExclusions
        val prev = m.getOrElse(dep0, Sets.empty) // getOrElse useful if we're passed duplicated stuff in dependencies
        if (prev.contains(dep)) {
          if (prev.size <= 1) {
            m -= dep0
          } else {
            val l = prev.remove(dep, _.exclusions.size, (a, b) => a.exclusions.subsetOf(b.exclusions))
            m += ((dep0, l))
          }
        }
      }

      DependencySet(set -- dependencies, m.toMap)
    }

  def setValues(newSet: Set[Dependency]): DependencySet = {
    val toAdd = newSet -- set
    val toRemove = set -- newSet
    addNoCheck(toAdd)
      .removeNoCheck(toRemove)
  }

}

object DependencySet {
  val empty = DependencySet(Set.empty, Map.empty)


  object Sets {
    def empty[T]: Sets[T] = Sets(TreeMap.empty, Map.empty, Map.empty)
  }

  final case class Sets[T](
    required: TreeMap[Int, Set[T]],
    children: Map[T, Set[T]],
    parents: Map[T, T]
  ) {

    def size: Int = parents.size + children.size

    def forall(f: T => Boolean): Boolean =
      (parents.keysIterator ++ children.keysIterator)
        .forall(f)

    def contains(t: T): Boolean =
      parents.contains(t) || children.contains(t)

    private def forceAdd(s: T, size: T => Int, subsetOf: (T, T) => Boolean): Sets[T] = {

      val n = size(s)

      val subsetOpt = required.filterKeys(_ <= n).iterator.flatMap(_._2.iterator).find {
        s0 => subsetOf(s0, s)
      }

      subsetOpt match {
        case None =>
          val required0 = required + (n -> (required.getOrElse(n, Set.empty) + s))
          val children0 = children + (s -> Set.empty[T])
          Sets(required0, children0, parents)
        case Some(subset) =>
          val children0 = children + (subset -> (children.getOrElse(subset, Set.empty) + s))
          val parents0 = parents + (s -> subset)
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
          val before = this.size
          val parents0 = parents -- children0
          val sets0 = Sets(required0, children - s, parents0)
          val r = children0.foldLeft(sets0)(_.forceAdd(_, size, subsetOf))
          val after = r.size
          assert(before - after == 1, s"after: $after, before: $before, right before: ${sets0.size}, removing $s, adding back $children0")
          r
        case None =>
          parents.get(s) match {
            case Some(parent) =>
              Sets(required, children + (parent -> (children.getOrElse(parent, Set.empty) - s)), parents - s)
            case None =>
              sys.error(s"Couldn't remove $s")
          }
      }
  }
}
