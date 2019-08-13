package coursier.graph

import coursier.core.{Module, Parse, Resolution, Version, VersionConstraint, VersionInterval}
import coursier.util.Print.Colors
import coursier.util.{Print, Tree}

final case class Conflict(
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

    def repr: String = {

      val colors0 = Colors.get(coursier.core.compatibility.hasConsole)

      val treeRepr = Tree(Seq(tree).toVector.sortBy(t => (t.module.organization.value, t.module.name.value, t.module.nameWithAttributes)))(_.dependees)
        .render { node =>
          if (node.excludedDependsOn)
            s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.reconciledVersion}"
          else
            s"${node.module}:${node.reconciledVersion}"
        }

      val assumeCompatibleVersions = Print.compatibleVersions(tree.dependsOnVersion, tree.dependsOnReconciledVersion)

      s"\n${tree.dependsOnModule.repr}:" +
        s"${if (assumeCompatibleVersions) colors0.yellow else colors0.red}${tree.dependsOnReconciledVersion}${colors0.reset} " +
        s"(${tree.dependsOnVersion} wanted)\n" + treeRepr
    }
  }

  def conflicted(resolution: Resolution, withExclusions: Boolean = false): Seq[Conflicted] = {

    val tree = ReverseModuleTree(resolution, withExclusions = withExclusions)

    val transitive = tree.flatMap { t =>
      t.dependees.collect {
        case d  if !d.excludedDependsOn && d.dependsOnReconciledVersion != d.dependsOnVersion =>
          Conflicted(d)
      }
    }

    val fromRoots = resolution.rootDependencies.flatMap { dep =>
      val version = resolution
        .reconciledVersions
        .getOrElse(dep.module, dep.version)
      val c = Parse.versionConstraint(dep.version)
      val v = Version(version)
      val matches =
        if (c.interval == VersionInterval.zero)
          c.preferred.contains(v)
        else
          c.interval.contains(v)
      if (matches)
        Nil
      else {
        val node = ReverseModuleTree.Node(
          dep.module,
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

  def apply(resolution: Resolution, withExclusions: Boolean = false): Seq[Conflict] =
    conflicted(resolution, withExclusions)
      .map(_.conflict)

}
