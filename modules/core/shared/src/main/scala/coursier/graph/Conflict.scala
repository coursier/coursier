package coursier.graph

import coursier.core.{Module, Resolution}

final case class Conflict(
  module: Module,
  version: String,
  wantedVersion: String,
  wasExcluded: Boolean,
  dependeeModule: Module,
  dependeeVersion: String
)

object Conflict {

  final case class Conflicted(tree: ReverseModuleTree) {
    def conflict: Conflict =
      Conflict(
        tree.dependsOnModule,
        tree.dependsOnReconciledVersion,
        tree.dependsOnVersion,
        tree.excludedDependsOn,
        tree.module,
        tree.reconciledVersion
      )
  }

  def conflicted(resolution: Resolution, withExclusions: Boolean = false): Seq[Conflicted] = {

    val tree = ReverseModuleTree(resolution, withExclusions = withExclusions)

    tree.flatMap { t =>
      t.dependees.collect {
        case d  if !d.excludedDependsOn && d.dependsOnReconciledVersion != d.dependsOnVersion =>
          Conflicted(d)
      }
    }
  }

  def apply(resolution: Resolution, withExclusions: Boolean = false): Seq[Conflict] =
    conflicted(resolution, withExclusions)
      .map(_.conflict)

}
