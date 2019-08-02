package coursier.graph

import coursier.core._

/** Simple dependency tree. */
sealed abstract class DependencyTree {
  def dependency: Dependency

  /** Whether this dependency was excluded by its parent (but landed in the classpath noneless via other dependencies. */
  def excluded: Boolean

  /** The final version of this dependency. */
  def reconciledVersion: String

  /** Dependencies of this node. */
  def children: Seq[DependencyTree]
}

object DependencyTree {

  def apply(
    resolution: Resolution,
    roots: Seq[Dependency] = null,
    withExclusions: Boolean = false
  ): Seq[DependencyTree] = {

    val roots0 = Option(roots).getOrElse(resolution.rootDependencies)

    roots0
      .map { dep =>
        Node(dep, excluded = false, resolution, withExclusions)
      }
  }

  def one(
    resolution: Resolution,
    root: Dependency,
    withExclusions: Boolean = false
  ): DependencyTree =
    Node(root, excluded = false, resolution, withExclusions)


  private case class Node(
    dependency: Dependency,
    excluded: Boolean,
    resolution: Resolution,
    withExclusions: Boolean
  ) extends DependencyTree {

    def reconciledVersion: String =
      resolution
        .reconciledVersions
        .getOrElse(dependency.module, dependency.version)

    // don't make that a val!! issues with cyclic dependencies
    // (see e.g. edu.illinois.cs.cogcomp:illinois-pos in the tests)
    def children: Seq[DependencyTree] =
      if (excluded)
        Nil
      else {
        val dep0 = dependency.withVersion(reconciledVersion)

        val dependencies = resolution
          .dependenciesOf(
            dep0,
            withRetainedVersions = false
          )
          .sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.version)
          }

        val dependencies0 = dependencies.map(_.moduleVersion).toSet

        def excluded = resolution
          .dependenciesOf(
            dep0.copy(exclusions = Set.empty),
            withRetainedVersions = false
          )
          .sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.version)
          }
          .collect {
            case trDep if !dependencies0(trDep.moduleVersion) =>
              Node(
                trDep,
                excluded = true,
                resolution,
                withExclusions
              )
          }

        dependencies.map(Node(_, excluded = false, resolution, withExclusions)) ++
          (if (withExclusions) excluded else Nil)
      }
  }

}
