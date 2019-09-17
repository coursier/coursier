package coursier.graph

import coursier.core.{Dependency, Module, Resolution}

/** Simple [[Module]] tree. */
sealed abstract class ModuleTree {
  def module: Module

  def reconciledVersion: String

  /** The final version of this dependency. */
  def retainedVersion: String

  /** The dependencies of this module. */
  def children: Seq[ModuleTree]
}

object ModuleTree {

  def apply(
    resolution: Resolution,
    roots: Seq[Dependency] = null
  ): Seq[ModuleTree] =
    apply(DependencyTree(resolution, roots))

  def one(
    resolution: Resolution,
    root: Dependency
  ): ModuleTree =
    Node(DependencyTree.one(resolution, root))

  def apply(dependencyTrees: Seq[DependencyTree]): Seq[ModuleTree] = {

    val dependencyTrees0 = dependencyTrees.filter(!_.excluded)

    val indices = dependencyTrees0
      .map(_.dependency.module)
      .zipWithIndex
      .reverse
      .toMap

    dependencyTrees0
      .groupBy(_.dependency.module)
      .toSeq
      .sortBy { // makes the output order deterministic
        case (m, _) =>
          indices(m)
      }
      .map {
        case (_, l) =>
          Node(l.head, l.tail: _*)
      }
  }


  private final case class Node(
    module: Module,
    reconciledVersion: String,
    retainedVersion: String,
    dependencyTrees: Seq[DependencyTree]
  ) extends ModuleTree {
    def children: Seq[ModuleTree] =
      ModuleTree(dependencyTrees.flatMap(_.children))
  }

  private object Node {
    def apply(
      dependencyTree: DependencyTree,
      others: DependencyTree*
    ): Node =
      new Node(
        dependencyTree.dependency.module,
        dependencyTree.reconciledVersion,
        dependencyTree.retainedVersion,
        dependencyTree +: others
      )
  }

}
