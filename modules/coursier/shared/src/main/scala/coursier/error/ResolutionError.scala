package coursier.error

import coursier.core.{Dependency, Module, Parse, Resolution}
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.ReverseModuleTree
import coursier.params.rule.Rule
import coursier.util.{Print, Tree}
import coursier.util.Print.{Colors, compatibleVersions}
import coursier.version.Version

sealed abstract class ResolutionError(
  val resolution: Resolution,
  message: String,
  cause: Throwable = null
) extends CoursierError(message, cause) {
  def errors: Seq[ResolutionError.Simple]
}

object ResolutionError {

  final class MaximumIterationReached(resolution: Resolution) extends Simple(
    resolution,
    "Maximum number of iterations reached"
  )

  final class CantDownloadModule(
    resolution: Resolution,
    val module: Module,
    val version: String,
    val perRepositoryErrors: Seq[String]
  ) extends Simple(
    resolution,
    s"Error downloading $module:$version\n" +
      perRepositoryErrors.map("  " + _.replace("\n", "  \n")).mkString("\n")
  )

  // Warning: currently, all conflicts in a resolution end-up in the same ConflictingDependencies instance
  final class ConflictingDependencies(
    resolution: Resolution,
    val dependencies: Set[Dependency]
  ) extends Simple(
    resolution,
    "Conflicting dependencies:\n" +
      {
        val roots = resolution.conflicts.map(_.module)
        val trees = ReverseModuleTree(resolution, roots = roots.toVector.sortBy(m => (m.organization.value, m.name.value, m.nameWithAttributes)))
        val colors0 = Colors.get(coursier.core.compatibility.coloredOutput)

        val renderedTrees = trees.map { t =>
          val rendered = Tree(t.dependees.toVector)(_.dependees)
            .customRender(assumeTopRoot = false, extraPrefix = "  ", extraSeparator = Some("")) { node =>
              if (node.excludedDependsOn)
                s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.reconciledVersion}"
              else if (node.dependsOnModule == t.module) {
                val assumeCompatibleVersions = compatibleVersions(node.dependsOnVersion, node.dependsOnReconciledVersion)

                s"${node.module}:${node.reconciledVersion} " +
                  (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
                  s"wants ${node.dependsOnVersion}" +
                  colors0.reset
              } else if (node.dependsOnVersion != node.dependsOnReconciledVersion) {
                val assumeCompatibleVersions = compatibleVersions(node.dependsOnVersion, node.dependsOnReconciledVersion)

                s"${node.module}:${node.reconciledVersion} " +
                  (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
                  s"wants ${node.dependsOnModule}:${node.dependsOnVersion}" +
                  colors0.reset
              } else
                s"${node.module}:${node.reconciledVersion}"
            }

          val dependeesWantVersions = t.dependees
            .map(_.dependsOnVersion)
            .distinct
            .map { ver =>
              val constraint = Parse.versionConstraint(ver)
              val sortWith = constraint.preferred.headOption
                .orElse(constraint.interval.from)
                .getOrElse(Version(""))
              ((constraint.preferred.isEmpty, sortWith), ver)
            }
            .sortBy(_._1)
            .map(_._2)
          s"${t.module.repr}:${dependeesWantVersions.mkString(" or ")} wanted by\n\n" +
            rendered + "\n"
        }

        renderedTrees.mkString("\n")
      }
  )

  sealed abstract class Simple(resolution: Resolution, message: String, cause: Throwable = null)
    extends ResolutionError(resolution, message, cause) {
    def errors: Seq[ResolutionError.Simple] =
      Seq(this)
  }

  final class Several(val head: Simple, val tail: Seq[Simple]) extends ResolutionError(
    head.resolution, (head +: tail).map(_.getMessage).mkString("\n")
  ) {
    def errors: Seq[ResolutionError.Simple] =
      head +: tail
  }

  def from(head: ResolutionError, tail: ResolutionError*): ResolutionError = {
    val errors = (head +: tail).flatMap {
      case s: Simple => Seq(s)
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
