package coursier.graph

import coursier.core.{Dependency, MinimizedExclusions, Resolution, VariantSelector}
import coursier.version.{Version, VersionConstraint}

import scala.annotation.tailrec

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
    initialDependency: Dependency,
    excluded: Boolean,
    resolution: Resolution,
    withExclusions: Boolean
  ) extends DependencyTree {

    lazy val dependency: Dependency = {

      @tailrec
      def relocation(dep: Dependency): Dependency = {
        val reconciledVersion = resolution.reconciledVersions.getOrElse(
          dep.module,
          sys.error(s"Cannot find ${dep.module.repr} in reconciled versions")
        )
        val dep0 =
          if (dep.versionConstraint == reconciledVersion) dep
          else dep.withVersionConstraint(reconciledVersion)
        val (_, proj) = resolution.projectCache0.getOrElse(
          dep0.moduleVersionConstraint,
          sys.error(
            s"Cannot find ${dep0.module.repr}:${dep0.versionConstraint.asString} in project cache"
          )
        )
        val mavenRelocatedOpt =
          if (proj.relocated && proj.dependencies0.lengthCompare(1) == 0)
            Some(proj.dependencies0.head._2)
          else
            None
        def gradleModuleRelocatedOpt =
          dep.variantSelector match {
            case attr: VariantSelector.AttributesBased =>
              if (proj.variants.isEmpty) None
              else {
                val variantOrError = proj.variantFor(attr)
                variantOrError match {
                  case Left(_)        => None
                  case Right(variant) => proj.isRelocatedVariant(variant)
                }
              }
            case _: VariantSelector.ConfigurationBased =>
              None
          }
        mavenRelocatedOpt.orElse(gradleModuleRelocatedOpt) match {
          case Some(relocatedTo) =>
            val relocatedTo0 =
              if (relocatedTo.variantSelector.isEmpty)
                relocatedTo.withVariantSelector(dep0.variantSelector)
              else
                relocatedTo
            relocation(relocatedTo0)
          case None =>
            dep
        }
      }

      if (resolution.isDone && resolution.conflicts.isEmpty && resolution.errors0.isEmpty)
        relocation(initialDependency)
      else
        initialDependency
    }

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
          .dependenciesOf0(
            dep0,
            withRetainedVersions = false,
            withReconciledVersions = true,
            withFallbackConfig = false
          )
          .toOption
          .getOrElse(Nil)
          .sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.versionConstraint)
          }

        val dependencies0 = dependencies.map(_.moduleVersionConstraint).toSet

        def excluded = resolution
          .dependenciesOf0(
            dep0.withMinimizedExclusions(MinimizedExclusions.zero),
            withRetainedVersions = false
          )
          .toOption
          .getOrElse(Nil)
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
