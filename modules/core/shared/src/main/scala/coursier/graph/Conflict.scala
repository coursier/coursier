package coursier.graph

import coursier.core.{Module, Parse, Resolution}
import coursier.version.{
  Version => Version0,
  VersionConstraint => VersionConstraint0,
  VersionInterval => VersionInterval0
}
import coursier.util.Print.{Colors, compatibleVersions}
import coursier.util.{Print, Tree}
import dataclass.data

@data class Conflict(
  module: Module,
  version0: Version0,
  wantedVersionConstraint: VersionConstraint0,
  wasExcluded: Boolean,
  dependeeModule: Module,
  dependeeVersionConstraint: VersionConstraint0
) {
  @deprecated("Use the override acception a Version and VersionConstraint-s instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    wantedVersion: String,
    wasExcluded: Boolean,
    dependeeModule: Module,
    dependeeVersion: String
  ) =
    this(
      module,
      Version0(version),
      VersionConstraint0(wantedVersion),
      wasExcluded,
      dependeeModule,
      VersionConstraint0(dependeeVersion)
    )

  @deprecated("Use version0 instead", "2.1.25")
  def version: String = version0.asString
  @deprecated("Use wantedVersionConstraint instead", "2.1.25")
  def wantedVersion: String = wantedVersionConstraint.asString
  @deprecated("Use dependeeVersionConstraint instead", "2.1.25")
  def dependeeVersion: String = dependeeVersionConstraint.asString

  @deprecated("Use withVersion0 instead", "2.1.25")
  def withVersion(newVersion: String): Conflict =
    if (newVersion == version) this
    else withVersion0(Version0(newVersion))
  @deprecated("Use withWantedVersionConstraint instead", "2.1.25")
  def withWantedVersion(newWantedVersion: String): Conflict =
    if (newWantedVersion == wantedVersion) this
    else withWantedVersionConstraint(VersionConstraint0(newWantedVersion))
  @deprecated("Use withDependeeVersionConstraint instead", "2.1.25")
  def withDependeeVersion(newDependeeVersion: String): Conflict =
    if (newDependeeVersion == dependeeVersion) this
    else withDependeeVersionConstraint(VersionConstraint0(newDependeeVersion))

  def repr: String =
    // FIXME Say something about wasExcluded?
    s"$module:${version0.asString} selected, but $dependeeModule:${dependeeVersionConstraint.asString} wanted ${wantedVersionConstraint.asString}"
}

object Conflict {

  @data class Conflicted(tree: ReverseModuleTree) {
    def conflict: Conflict =
      Conflict(
        tree.dependsOnModule,
        tree.dependsOnRetainedVersion0,
        tree.dependsOnVersionConstraint,
        tree.excludedDependsOn,
        tree.module,
        tree.reconciledVersionConstraint
      )

    def repr: String = {

      val colors0 = Colors.get(coursier.core.compatibility.coloredOutput)

      val tree0 = Tree(
        Seq(tree).toVector.sortBy(t =>
          (t.module.organization.value, t.module.name.value, t.module.nameWithAttributes)
        )
      )(_.dependees)
      val treeRepr = tree0.render { node =>
        if (node.excludedDependsOn)
          s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.retainedVersion0.asString}"
        else if (node.dependsOnVersionConstraint != node.dependsOnRetainedVersion0) {
          val assumeCompatibleVersions =
            compatibleVersions(node.dependsOnVersionConstraint, node.dependsOnRetainedVersion0)

          s"${node.module}:${node.retainedVersion0.asString} " +
            (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
            s"wants ${node.dependsOnModule}:${node.dependsOnVersionConstraint.asString}" +
            colors0.reset
        }
        else
          s"${node.module}:${node.retainedVersion0.asString}"
      }

      val assumeCompatibleVersions =
        Print.compatibleVersions(tree.dependsOnVersionConstraint, tree.dependsOnRetainedVersion0)

      System.lineSeparator() + s"${tree.dependsOnModule.repr}:" +
        s"${if (assumeCompatibleVersions) colors0.yellow
          else colors0.red}${tree.dependsOnRetainedVersion0.asString}${colors0.reset} " +
        s"(${tree.dependsOnVersionConstraint.asString} wanted)" + System.lineSeparator() + treeRepr
    }
  }

  def conflicted(
    resolution: Resolution,
    withExclusions: Boolean = false,
    semVer: Boolean = false
  ): Seq[Conflicted] = {

    val tree = ReverseModuleTree(resolution, withExclusions = withExclusions)

    def compatible(wanted: VersionConstraint0, selected: Version0): Boolean =
      wanted.asString == selected.asString || {
        if (wanted.interval == VersionInterval0.zero)
          if (semVer)
            wanted.preferred.exists(_.items.take(2) == selected.items.take(2))
          else
            wanted.preferred.contains(selected)
        else
          wanted.interval.contains(selected)
      }

    val transitive = tree.flatMap { t =>
      t.dependees.collect {
        case d
            if !d.excludedDependsOn &&
            !compatible(d.dependsOnVersionConstraint, d.dependsOnRetainedVersion0) =>
          Conflicted(d)
      }
    }

    val fromRoots = resolution.rootDependencies.flatMap { dep =>
      val version = resolution
        .retainedVersions
        .getOrElse(dep.module, sys.error(s"Cannot find ${dep.module} in reconciled versions"))
      val matches =
        dep.versionConstraint.asString.isEmpty || compatible(dep.versionConstraint, version)
      if (matches)
        Nil
      else {
        val node = ReverseModuleTree.Node(
          dep.module,
          dep.versionConstraint,
          version,
          dep.module,
          dep.versionConstraint,
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

  def apply(
    resolution: Resolution,
    withExclusions: Boolean = false,
    semVer: Boolean = false
  ): Seq[Conflict] =
    conflicted(resolution, withExclusions, semVer)
      .map(_.conflict)

  @deprecated("Use the override acception a Version and VersionConstraint-s instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    wantedVersion: String,
    wasExcluded: Boolean,
    dependeeModule: Module,
    dependeeVersion: String
  ): Conflict =
    apply(
      module,
      Version0(version),
      VersionConstraint0(wantedVersion),
      wasExcluded,
      dependeeModule,
      VersionConstraint0(dependeeVersion)
    )
}
