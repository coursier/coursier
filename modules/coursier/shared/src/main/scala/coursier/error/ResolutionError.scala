package coursier.error

import coursier.core.{Dependency, Module, Resolution}
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.ReverseModuleTree
import coursier.params.rule.Rule
import coursier.util.Print.{Colors, compatibleVersions}
import coursier.util.Tree
import coursier.version.{Version, VersionConstraint}

sealed abstract class ResolutionError(
  val resolution: Resolution,
  message: String,
  cause: Throwable = null
) extends CoursierError(message, cause) {
  def errors: Seq[ResolutionError.Simple]
}

object ResolutionError {

  // format: off
  final class MaximumIterationReached(resolution: Resolution) extends Simple(
    resolution,
    "Maximum number of iterations reached"
  )
  // format: on

  // format: off
  final class CantDownloadModule(
    resolution: Resolution,
    val module: Module,
    val versionConstraint: VersionConstraint,
    val perRepositoryErrors: Seq[String]
  ) extends Simple(
    resolution,
    s"Error downloading $module:${versionConstraint.asString}" + System.lineSeparator() +
      perRepositoryErrors
        .map { err =>
          "  " + err.replace(System.lineSeparator(), System.lineSeparator() + "  ")
        }
        .mkString(System.lineSeparator())
  ) {
    @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
    def this(
      resolution: Resolution,
      module: Module,
      version: String,
      perRepositoryErrors: Seq[String]
    ) = this(
      resolution,
      module,
      VersionConstraint(version),
      perRepositoryErrors
    )

    @deprecated("Use version0 instead", "2.1.25")
    def version: String = versionConstraint.asString
  }
  // format: on

  private[coursier] def conflictingDependenciesErrorMessage(
    resolution: Resolution,
    colors: Colors = Colors.get(coursier.core.compatibility.coloredOutput)
  ): String =
    "Conflicting dependencies:" + System.lineSeparator() + {
      val roots = resolution.conflicts.map(_.module)
      val trees = ReverseModuleTree(
        resolution,
        roots =
          roots.toVector.sortBy(m => (m.organization.value, m.name.value, m.nameWithAttributes))
      )
      val renderedTrees = trees.map { t =>
        val rendered = Tree(t.dependees.toVector)(_.dependees)
          .customRender(assumeTopRoot = false, extraPrefix = "  ", extraSeparator = Some("")) {
            node =>
              if (node.excludedDependsOn)
                s"${colors.yellow}(excluded by)${colors.reset} ${node.module}:${node.retainedVersion0.asString}"
              else if (node.dependsOnModule == t.module) {
                val (retainedVersion, assumeCompatibleVersions) =
                  if (node.retainedVersion0.asString.isEmpty && node.module == node.dependsOnModule)
                    (
                      node.reconciledVersionConstraint.asString,
                      true
                    )
                  else
                    (
                      node.retainedVersion0.asString,
                      compatibleVersions(
                        node.dependsOnVersionConstraint,
                        node.dependsOnRetainedVersion0
                      )
                    )

                s"${node.module}:$retainedVersion " +
                  (if (assumeCompatibleVersions) colors.yellow else colors.red) +
                  s"wants ${node.dependsOnVersionConstraint.asString}" +
                  colors.reset
              }
              else if (
                node.dependsOnVersionConstraint.asString != node.dependsOnRetainedVersion0.asString
              ) {
                val assumeCompatibleVersions =
                  compatibleVersions(
                    node.dependsOnVersionConstraint,
                    node.dependsOnRetainedVersion0
                  )

                s"${node.module}:${node.retainedVersion0.asString} " +
                  (if (assumeCompatibleVersions) colors.yellow else colors.red) +
                  s"wants ${node.dependsOnModule}:${node.dependsOnVersionConstraint.asString}" +
                  colors.reset
              }
              else
                s"${node.module}:${node.retainedVersion0.asString}"
          }

        val dependeesWantVersions = t.dependees
          .map(_.dependsOnVersionConstraint)
          .distinct
          .map { ver =>
            val sortWith = ver.preferred.headOption
              .orElse(ver.interval.from)
              .getOrElse(Version.zero)
            ((ver.preferred.isEmpty, sortWith), ver)
          }
          .sortBy(_._1)
          .map(_._2)
        s"${t.module.repr}:${dependeesWantVersions.map(_.asString).mkString(" or ")} wanted by" +
          System.lineSeparator() +
          System.lineSeparator() +
          rendered + System.lineSeparator()
      }

      renderedTrees.mkString(System.lineSeparator())
    }

  // Warning: currently, all conflicts in a resolution end-up in the same ConflictingDependencies instance
  final class ConflictingDependencies(
    resolution: Resolution,
    val dependencies: Set[Dependency]
  ) extends Simple(resolution, conflictingDependenciesErrorMessage(resolution))

  sealed abstract class Simple(resolution: Resolution, message: String, cause: Throwable = null)
      extends ResolutionError(resolution, message, cause) {
    def errors: Seq[ResolutionError.Simple] =
      Seq(this)
  }

  // format: off
  final class Several(val head: Simple, val tail: Seq[Simple]) extends ResolutionError(
    head.resolution,
    (head +: tail).map(_.getMessage).mkString(System.lineSeparator())
  ) {
    // format: on
    def errors: Seq[ResolutionError.Simple] =
      head +: tail
  }

  def from(head: ResolutionError, tail: ResolutionError*): ResolutionError = {
    val errors = (head +: tail).flatMap {
      case s: Simple  => Seq(s)
      case l: Several => l.head +: l.tail
    }
    assert(errors.nonEmpty)
    if (errors.tail.isEmpty)
      errors.head
    else
      new Several(errors.head, errors.tail)
  }

  abstract class UnsatisfiableRule(
    resolution: Resolution,
    val rule: Rule,
    val conflict: UnsatisfiedRule,
    message: String
  ) extends Simple(resolution, message, conflict)

}
