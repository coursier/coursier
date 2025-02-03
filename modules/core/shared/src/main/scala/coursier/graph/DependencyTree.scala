package coursier.graph

import coursier.core.{Dependency, MinimizedExclusions, Resolution}
import coursier.version.{Version, VersionConstraint}

/** Simple dependency tree. */
sealed abstract class DependencyTree {
  def dependency: Dependency

  /** Whether this dependency was excluded by its parent (but landed in the classpath nonetheless
    * via other dependencies.
    */
  def excluded: Boolean

  def reconciledVersionConstraint: VersionConstraint

  @deprecated("Use reconciledVersion0 instead", "2.1.25")
  def reconciledVersion: String =
    reconciledVersionConstraint.asString

  /** The final version of this dependency. */
  def retainedVersion0: Version

  @deprecated("Use retainedVersion0 instead", "2.1.25")
  def retainedVersion: String =
    retainedVersion0.asString

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

    def reconciledVersionConstraint: VersionConstraint =
      resolution
        .reconciledVersions
        .getOrElse(dependency.module, dependency.versionConstraint)

    def retainedVersion0: Version =
      resolution
        .retainedVersions
        .getOrElse(
          dependency.module,
          Version.zero
          // sys.error(s"${dependency.module} not found in retained versions (got ${resolution.retainedVersions.keys.toVector.map(_.repr).sorted})")
        )

    // don't make that a val!! issues with cyclic dependencies
    // (see e.g. edu.illinois.cs.cogcomp:illinois-pos in the tests)
    def children: Seq[DependencyTree] =
      if (excluded)
        Nil
      else {
        val dep0 = dependency.withVersionConstraint(reconciledVersionConstraint)

        val dependencies = resolution
          .dependenciesOf(
            dep0,
            withRetainedVersions = false
          )
          .sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.versionConstraint)
          }

        val dependencies0 = dependencies.map(_.moduleVersionConstraint).toSet

        def excluded = resolution
          .dependenciesOf(
            dep0.withMinimizedExclusions(MinimizedExclusions.zero),
            withRetainedVersions = false
          )
          .sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.versionConstraint)
          }
          .collect {
            case trDep if !dependencies0(trDep.moduleVersionConstraint) =>
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
