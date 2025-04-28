package coursier.graph

import coursier.core.{Module, Resolution}
import coursier.version.{Version, VersionConstraint}

import scala.collection.compat._
import scala.collection.mutable

/** Tree allowing to walk the dependency graph from dependency to dependees. */
sealed abstract class ReverseModuleTree {
  def module: Module

  /** The final version of this dependency. */
  def reconciledVersionConstraint: VersionConstraint

  @deprecated("Use retainedVersion0 instead", "2.1.25")
  def reconciledVersion: String =
    reconciledVersionConstraint.asString

  def retainedVersion0: Version

  @deprecated("Use retainedVersion0 instead", "2.1.25")
  def retainedVersion: String =
    retainedVersion0.asString

  // some info about what we depend on, our "parent" in this inverse tree

  /** Module of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    */
  def dependsOnModule: Module

  /** Version of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    *
    * This is the version this module explicitly depends on.
    */
  def dependsOnVersionConstraint: VersionConstraint

  @deprecated("Use retainedVersion0 instead", "2.1.25")
  def dependsOnVersion: String =
    dependsOnVersionConstraint.asString

  /** Final version of the parent dependency of to this node.
    *
    * This node is a dependee. This method corresponds to what we depend on.
    *
    * This is the version that was selected during resolution.
    */
  def dependsOnRetainedVersion0: Version

  @deprecated(
    "Use retainedVersion0 instead - this actually returns a \"retained\" version",
    "2.1.25"
  )
  def dependsOnReconciledVersion: String =
    dependsOnRetainedVersion0.asString

  /** Whether the parent dependency was excluded by us, but landed anyway in the classpath.
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

  def fromModuleTree(
    roots: Seq[Module],
    moduleTrees: Seq[ModuleTree],
    rootDependencies: Map[Module, VersionConstraint]
  ): Seq[ReverseModuleTree] = {

    // Some assumptions about moduleTrees, and the ModuleTree-s we get though their children:
    // - any two ModuleTree-s with the same module are assumed to have the same reconciledVersion
    // - if recursing on children leads to cycles, it is assumed we'll cycle through ModuleTree instances that are ==
    //   (that is, keeping a set of the already seen ModuleTree should be enough to detect those cycles).

    // Some *non*-assumptions:
    // - ModuleTree-s with the same module have the same children: this doesn't necessarily hold, as two ModuleTree-s
    //   may originate from two different dependencies (with different exclusions for example), bringing possibly
    //   different children dependencies.

    val alreadySeen = new mutable.HashSet[ModuleTree]
    val dependees =
      new mutable.HashMap[Module, mutable.HashSet[(Module, VersionConstraint, Boolean)]]
    val versions = new mutable.HashMap[Module, (VersionConstraint, Version)]
    val toCheck  = new mutable.Queue[ModuleTree]

    toCheck ++= moduleTrees

    while (toCheck.nonEmpty) {
      val elem = toCheck.dequeue()
      alreadySeen += elem
      versions.put(elem.module, (elem.reconciledVersionConstraint, elem.retainedVersion0))
      val children = elem.children
      toCheck ++= children.filterNot(alreadySeen)
      for (c <- children) {
        val b = dependees.getOrElseUpdate(
          c.module,
          new mutable.HashSet[(Module, VersionConstraint, Boolean)]
        )
        b.add((elem.module, c.reconciledVersionConstraint, false))
      }
    }

    val dependees0 = dependees
      .toMap
      .view
      .mapValues(_.toVector.sortBy(t =>
        (t._1.organization.value, t._1.name.value, t._1.nameWithAttributes)
      ))
      .iterator
      .toMap
    val versions0 = versions.toMap

    for {
      m                      <- roots
      (reconciled, retained) <- versions.get(m)
    } yield Node(
      m,
      reconciled,
      retained,
      m,
      reconciled,
      retained,
      excludedDependsOn = false,
      dependees0,
      versions0,
      Map.empty
    )
  }

  @deprecated("Use the override accepting rootDependencies", "2.1.25")
  def fromModuleTree(
    roots: Seq[Module],
    moduleTrees: Seq[ModuleTree]
  ): Seq[ReverseModuleTree] =
    fromModuleTree(roots, moduleTrees, Map.empty)

  def fromDependencyTree(
    roots: Seq[Module],
    dependencyTrees: Seq[DependencyTree],
    rootDependencies: Map[Module, Seq[VersionConstraint]]
  ): Seq[ReverseModuleTree] = {

    val alreadySeen = new mutable.HashSet[DependencyTree]
    val dependees =
      new mutable.HashMap[Module, mutable.HashSet[(Module, VersionConstraint, Boolean)]]
    val versions = new mutable.HashMap[Module, (VersionConstraint, Version)]
    val toCheck  = new mutable.Queue[DependencyTree]

    toCheck ++= dependencyTrees

    while (toCheck.nonEmpty) {
      val elem = toCheck.dequeue()
      alreadySeen += elem
      versions.put(
        elem.dependency.module,
        (elem.reconciledVersionConstraint, elem.retainedVersion0)
      )
      val children = elem.children
      toCheck ++= children.filterNot(alreadySeen)
      for (c <- children) {
        val b = dependees.getOrElseUpdate(
          c.dependency.module,
          new mutable.HashSet[(Module, VersionConstraint, Boolean)]
        )
        b.add((elem.dependency.module, c.dependency.versionConstraint, c.excluded))
      }
    }

    val dependees0 = dependees
      .toMap
      .view
      .mapValues(_.toVector.sortBy(t =>
        (t._1.organization.value, t._1.name.value, t._1.nameWithAttributes)
      ))
      .iterator
      .toMap
    val versions0 = versions.toMap

    for {
      m                      <- roots
      (reconciled, retained) <- versions.get(m)
    } yield Node(
      m,
      reconciled,
      retained,
      m,
      reconciled,
      retained,
      excludedDependsOn = false,
      dependees0,
      versions0,
      rootDependencies
    )
  }

  @deprecated("Use the override accepting rootDependencies", "2.1.25")
  def fromDependencyTree(
    roots: Seq[Module],
    dependencyTrees: Seq[DependencyTree]
  ): Seq[ReverseModuleTree] =
    fromDependencyTree(roots, dependencyTrees, Map.empty)

  def apply(
    resolution: Resolution,
    roots: Seq[Module] = null,
    withExclusions: Boolean = false
  ): Seq[ReverseModuleTree] = {
    val t      = DependencyTree(resolution, withExclusions = withExclusions)
    val roots0 = Option(roots).getOrElse(resolution.minDependencies.toVector.map(_.module))
    fromDependencyTree(
      roots0,
      t,
      resolution.rootDependencies
        .groupBy(_.module)
        .map {
          case (mod, deps0) =>
            mod -> deps0.map(_.versionConstraint).distinct
        }
    )
  }

  private[graph] final case class Node(
    module: Module,
    reconciledVersionConstraint: VersionConstraint,
    retainedVersion0: Version,
    dependsOnModule: Module,
    dependsOnVersionConstraint: VersionConstraint,
    dependsOnRetainedVersion0: Version,
    excludedDependsOn: Boolean,
    allDependees: Map[Module, Seq[(Module, VersionConstraint, Boolean)]],
    versions: Map[Module, (VersionConstraint, Version)],
    rootDependencies: Map[Module, Seq[VersionConstraint]]
  ) extends ReverseModuleTree {
    private def dependeesFromRoot =
      for {
        wantVer       <- rootDependencies.getOrElse(module, Nil)
        (_, retained) <- versions.get(module)
      } yield Node(
        module,
        wantVer,
        retained,
        module,
        wantVer,
        retainedVersion0,
        excludedDependsOn = false,
        Map.empty,
        versions,
        rootDependencies = Map.empty
      )
    private def actualDependees: Seq[Node] =
      for {
        (m, wantVer, excl)     <- allDependees.getOrElse(module, Nil)
        (reconciled, retained) <- versions.get(m)
      } yield Node(
        m,
        reconciled,
        retained,
        module,
        wantVer,
        retainedVersion0,
        excl,
        allDependees,
        versions,
        rootDependencies
      )
    def dependees: Seq[Node] =
      dependeesFromRoot ++ actualDependees
  }

}
