package coursier.graph

import coursier.core.{Module, Resolution}

import scala.collection.mutable

/** Tree allowing to walk the dependency graph from dependency to dependees. */
sealed abstract class ReverseModuleTree {
  def module: Module

  /** The final version of this dependency. */
  def reconciledVersion: String

  // some info about what we depend on, our "parent" in this inverse tree

  /**
    * Module of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    */
  def dependsOnModule: Module

  /**
    * Version of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    *
    * This is the version this module explicitly depends on.
    */
  def dependsOnVersion: String

  /**
    * Final version of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    *
    * This is the version that was selected during resolution.
    */
  def dependsOnReconciledVersion: String

  /**
    * Whether the parent dependency was excluded by us, but landed anyway in the classpath.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    *
    * This says whether this dependency was excluded by us, but landed anyway in the classpath.
    */
  def excludedDependsOn: Boolean

  /** Dependees of this module. */
  def dependees: Seq[ReverseModuleTree]
}

object ReverseModuleTree {

  def fromModuleTree(roots: Seq[Module], moduleTrees: Seq[ModuleTree]): Seq[ReverseModuleTree] = {

    // Some assumptions about moduleTrees, and the ModuleTree-s we get though their children:
    // - any two ModuleTree-s with the same module are assumed to have the same reconciledVersion
    // - if recursing on children leads to cycles, it is assumed we'll cycle through ModuleTree instances that are ==
    //   (that is, keeping a set of the already seen ModuleTree should be enough to detect those cycles).

    // Some *non*-assumptions:
    // - ModuleTree-s with the same module have the same children: this doesn't necessarily hold, as two ModuleTree-s
    //   may originate from two different dependencies (with different exclusions for example), bringing possibly
    //   different children dependencies.

    val alreadySeen = new mutable.HashSet[ModuleTree]
    val dependees = new mutable.HashMap[Module, mutable.HashSet[(Module, String, Boolean)]]
    val versions = new mutable.HashMap[Module, String]
    val toCheck = new mutable.Queue[ModuleTree]

    toCheck ++= moduleTrees

    while (toCheck.nonEmpty) {
      val elem = toCheck.dequeue()
      alreadySeen += elem
      versions.put(elem.module, elem.reconciledVersion)
      val children = elem.children
      toCheck ++= children.filterNot(alreadySeen)
      for (c <- children) {
        val b = dependees.getOrElseUpdate(c.module, new mutable.HashSet[(Module, String, Boolean)])
        b.add((elem.module, c.reconciledVersion, false))
      }
    }

    val dependees0 = dependees
      .toMap
      .mapValues(_.toVector.sortBy(t => (t._1.organization.value, t._1.name.value, t._1.nameWithAttributes)))
      .iterator
      .toMap
    val versions0 = versions.toMap

    for {
      m <- roots
      v <- versions.get(m)
    } yield Node(m, v, m, v, v, excludedDependsOn = false, dependees0, versions0)
  }

  def fromDependencyTree(roots: Seq[Module], dependencyTrees: Seq[DependencyTree]): Seq[ReverseModuleTree] = {

    val alreadySeen = new mutable.HashSet[DependencyTree]
    val dependees = new mutable.HashMap[Module, mutable.HashSet[(Module, String, Boolean)]]
    val versions = new mutable.HashMap[Module, String]
    val toCheck = new mutable.Queue[DependencyTree]

    toCheck ++= dependencyTrees

    while (toCheck.nonEmpty) {
      val elem = toCheck.dequeue()
      alreadySeen += elem
      versions.put(elem.dependency.module, elem.reconciledVersion)
      val children = elem.children
      toCheck ++= children.filterNot(alreadySeen)
      for (c <- children) {
        val b = dependees.getOrElseUpdate(c.dependency.module, new mutable.HashSet[(Module, String, Boolean)])
        b.add((elem.dependency.module, c.dependency.version, c.excluded))
      }
    }

    val dependees0 = dependees
      .toMap
      .mapValues(_.toVector.sortBy(t => (t._1.organization.value, t._1.name.value, t._1.nameWithAttributes)))
      .iterator
      .toMap
    val versions0 = versions.toMap

    for {
      m <- roots
      v <- versions.get(m)
    } yield Node(m, v, m, v, v, excludedDependsOn = false, dependees0, versions0)
  }

  def apply(
    resolution: Resolution,
    roots: Seq[Module] = null,
    withExclusions: Boolean = false
  ): Seq[ReverseModuleTree] = {
    val t = DependencyTree(resolution, withExclusions = withExclusions)
    val roots0 = Option(roots).getOrElse(resolution.minDependencies.toVector.map(_.module))
    fromDependencyTree(roots0, t)
  }

  private final case class Node(
    module: Module,
    reconciledVersion: String,
    dependsOnModule: Module,
    dependsOnVersion: String,
    dependsOnReconciledVersion: String,
    excludedDependsOn: Boolean,
    allDependees: Map[Module, Seq[(Module, String, Boolean)]],
    versions: Map[Module, String]
  ) extends ReverseModuleTree {
    def dependees: Seq[Node] =
      for {
        (m, wantVer, excl) <- allDependees.getOrElse(module, Nil)
        v <- versions.get(m)
      } yield Node(m, v, module, wantVer, reconciledVersion, excl, allDependees, versions)
  }

}
