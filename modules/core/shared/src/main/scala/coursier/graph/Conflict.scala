package coursier.graph

import coursier.core.{Module, Parse, Resolution}
import coursier.util.Print.Colors
import coursier.util.{Print, Tree}
import coursier.util.Print.compatibleVersions
import coursier.version.{VersionCompatibility, VersionConstraint, VersionInterval}
import dataclass.data

@data class Conflict(
  module: Module,
  version: String,
  wantedVersion: String,
  wasExcluded: Boolean,
  dependeeModule: Module,
  dependeeVersion: String
) {
  def repr: String =
    // FIXME Say something about wasExcluded?
    s"$module:$version selected, but $dependeeModule:$dependeeVersion wanted $wantedVersion"
}

object Conflict {

  @data class Conflicted(tree: ReverseModuleTree) {
    def conflict: Conflict =
      Conflict(
        tree.dependsOnModule,
        tree.dependsOnReconciledVersion,
        tree.dependsOnVersion,
        tree.excludedDependsOn,
        tree.module,
        tree.reconciledVersion
      )

    def repr: String = {

      val colors0 = Colors.get(coursier.core.compatibility.coloredOutput)

      val treeRepr = Tree(Seq(tree).toVector.sortBy(t => (t.module.organization.value, t.module.name.value, t.module.nameWithAttributes)))(_.dependees)
        .render { node =>
          if (node.excludedDependsOn)
            s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.reconciledVersion}"
          else if (node.dependsOnVersion != node.dependsOnReconciledVersion) {
            val assumeCompatibleVersions = compatibleVersions(node.dependsOnVersion, node.dependsOnReconciledVersion)

            s"${node.module}:${node.reconciledVersion} " +
              (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
              s"wants ${node.dependsOnModule}:${node.dependsOnVersion}" +
              colors0.reset
          } else
            s"${node.module}:${node.reconciledVersion}"
        }

      val assumeCompatibleVersions = Print.compatibleVersions(tree.dependsOnVersion, tree.dependsOnReconciledVersion)

      s"\n${tree.dependsOnModule.repr}:" +
        s"${if (assumeCompatibleVersions) colors0.yellow else colors0.red}${tree.dependsOnReconciledVersion}${colors0.reset} " +
        s"(${tree.dependsOnVersion} wanted)\n" + treeRepr
    }
  }

  def conflicted(
    resolution: Resolution,
    withExclusions: Boolean = false,
    semVer: Boolean = false
  ): Seq[Conflicted] = {

    val tree = ReverseModuleTree(resolution, withExclusions = withExclusions)

    def compatible(wanted: String, selected: String): Boolean =
      if (semVer)
        VersionCompatibility.PackVer.isCompatible(wanted, selected)
      else
        VersionCompatibility.Strict.isCompatible(wanted, selected)

    val transitive = tree.flatMap { t =>
      t.dependees.collect {
        case d  if !d.excludedDependsOn && !compatible(d.dependsOnReconciledVersion, d.dependsOnVersion) =>
          Conflicted(d)
      }
    }

    val fromRoots = resolution.rootDependencies.flatMap { dep =>
      val version = resolution
        .reconciledVersions
        .getOrElse(dep.module, dep.version)
      val matches = compatible(dep.version, version)
      if (matches)
        Nil
      else {
        val node = ReverseModuleTree.Node(
          dep.module,
          dep.version,
          dep.version,
          dep.module,
          dep.version,
          version,
          excludedDependsOn = false,
          Map.empty,
          Map.empty
        )
        Seq(Conflicted(node))
      }
    }

    fromRoots ++ transitive
  }

  def apply(resolution: Resolution, withExclusions: Boolean = false, semVer: Boolean = false): Seq[Conflict] =
    conflicted(resolution, withExclusions, semVer)
      .map(_.conflict)

}
