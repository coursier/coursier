package coursier.util

import coursier.core.{
  Attributes,
  Configuration,
  Dependency,
  Module,
  Project,
  Resolution,
  VariantSelector
}
import coursier.graph.{Conflict, DependencyTree, ReverseModuleTree}
import coursier.version.{Version, VersionConstraint, VersionInterval}
import dataclass.data

object Print {

  object Colors {
    private val `with`: Colors    = Colors(Console.RED, Console.YELLOW, Console.RESET)
    private val `without`: Colors = Colors("", "", "")

    def get(colors: Boolean): Colors = if (colors) `with` else `without`
  }

  @data class Colors private (red: String, yellow: String, reset: String)

  def dependency(dep: Dependency): String =
    dependency(dep, printExclusions = false)

  def dependency(dep: Dependency, printExclusions: Boolean): String = {

    def exclusionsStr = dep
      .minimizedExclusions
      .toSet()
      .toVector
      .sorted
      .map {
        case (org, name) =>
          s"\n  exclude($org, $name)"
      }
      .mkString

    s"${dep.module}:${dep.versionConstraint.asString}:${dep.variantSelector.repr}" +
      (if (printExclusions) exclusionsStr else "")
  }

  def dependenciesUnknownConfigs0(
    deps: Seq[Dependency],
    projects: Map[(Module, VersionConstraint), Project]
  ): String =
    dependenciesUnknownConfigs0(deps, projects, printExclusions = false)

  @deprecated("Use dependenciesUnknownConfigs0 instead", "2.1.25")
  def dependenciesUnknownConfigs(
    deps: Seq[Dependency],
    projects: Map[(Module, String), Project]
  ): String =
    dependenciesUnknownConfigs0(
      deps,
      projects.map {
        case ((mod, ver), value) =>
          ((mod, VersionConstraint(ver)), value)
      }
    )

  def dependenciesUnknownConfigs0(
    deps: Seq[Dependency],
    projects: Map[(Module, VersionConstraint), Project],
    printExclusions: Boolean,
    useFinalVersions: Boolean = true,
    reorder: Boolean = false
  ): String = {

    val deps0 =
      if (useFinalVersions)
        deps.map { dep =>
          dep.withVersionConstraint(
            projects
              .get(dep.moduleVersionConstraint)
              .fold(dep.versionConstraint)(proj => VersionConstraint.fromVersion(proj.version0))
          )
        }
      else
        deps

    val deps1 =
      if (reorder)
        deps0
          .groupBy { dep =>
            dep
              .withVariantSelector(VariantSelector.emptyConfiguration)
              .withAttributes(Attributes.empty)
          }
          .toVector
          .flatMap {
            case (k, l) =>
              val split = l.map { dep =>
                dep.variantSelector match {
                  case c: VariantSelector.ConfigurationBased =>
                    Left(c.configuration)
                  case _: VariantSelector.AttributesBased =>
                    Right(dep)
                }
              }
              val configurations = split.collect {
                case Left(conf) => conf
              }
              val others = split.collect {
                case Right(dep0) => dep0
              }
              val updatedConfDep =
                if (configurations.isEmpty) Nil
                else {
                  val conf = Configuration.join(configurations.toVector.sorted.distinct: _*)
                  Seq(k.withVariantSelector(VariantSelector.ConfigurationBased(conf)))
                }
              updatedConfDep ++ others
          }
          .sortBy { dep =>
            (dep.module.organization, dep.module.name, dep.module.toString, dep.versionConstraint)
          }
      else
        deps0

    val l  = deps1.map(dependency(_, printExclusions))
    val l0 = if (reorder) l.distinct else l
    l0.mkString(System.lineSeparator())
  }

  @deprecated("Use dependenciesUnknownConfigs0 instead", "2.1.25")
  def dependenciesUnknownConfigs(
    deps: Seq[Dependency],
    projects: Map[(Module, String), Project],
    printExclusions: Boolean,
    useFinalVersions: Boolean = true,
    reorder: Boolean = false
  ): String =
    dependenciesUnknownConfigs0(
      deps,
      projects.map {
        case ((mod, ver), value) =>
          ((mod, VersionConstraint(ver)), value)
      },
      printExclusions,
      useFinalVersions,
      reorder
    )

  def compatibleVersions(compatibleWith: VersionConstraint, selected: Version): Boolean =
    // too loose for now
    // e.g. RCs and milestones should not be considered compatible with subsequent non-RC or
    // milestone versions - possibly not with each other either
    if (compatibleWith.interval == VersionInterval.zero)
      compatibleWith.preferred.exists { v =>
        v.repr.split('.').take(2).toSeq == selected.repr.split('.').take(2).toSeq
      }
    else
      compatibleWith.interval.contains(selected)

  @deprecated("Use the override accepting a VersionConstraint and a Version instead", "2.1.25")
  def compatibleVersions(compatibleWith: String, selected: String): Boolean =
    compatibleVersions(VersionConstraint(compatibleWith), Version(selected))

  def dependencyTree(
    resolution: Resolution,
    roots: Seq[Dependency] = null,
    printExclusions: Boolean = false,
    reverse: Boolean = false,
    colors: Boolean = true
  ): String = {

    val colors0 = Colors.get(colors)

    if (reverse) {
      val roots0 = Option(roots).getOrElse(resolution.minDependencies.toSeq)

      val t = ReverseModuleTree.fromDependencyTree(
        roots0.map(_.module).distinct,
        DependencyTree(resolution, withExclusions = printExclusions),
        resolution.rootDependencies
          .groupBy(_.module)
          .map {
            case (mod, deps0) =>
              (mod, deps0.map(_.versionConstraint).distinct)
          }
      )

      val tree0 = Tree(
        t.toVector.sortBy(t =>
          (t.module.organization.value, t.module.name.value, t.module.nameWithAttributes)
        )
      ) { tree =>
        tree.dependees.filter { depTree =>
          depTree.module != tree.module || depTree.dependees.nonEmpty
        }
      }
      tree0.render { node =>
        if (node.excludedDependsOn)
          s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.retainedVersion0.asString}"
        else if (
          node.dependsOnVersionConstraint.asString == node.dependsOnRetainedVersion0.asString
        )
          s"${node.module}:${node.retainedVersion0.asString}"
        else {
          val assumeCompatibleVersions =
            compatibleVersions(node.dependsOnVersionConstraint, node.dependsOnRetainedVersion0)

          s"${node.module}:${node.retainedVersion0.asString} " +
            (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
            s"${node.dependsOnModule}:${node.dependsOnVersionConstraint.asString} -> ${node.dependsOnRetainedVersion0.asString}" +
            colors0.reset
        }
      }
    }
    else {
      val roots0 = Option(roots).getOrElse(resolution.rootDependencies)
      val t      = DependencyTree(resolution, roots0, withExclusions = printExclusions)
      Tree(t.toVector)(_.children)
        .render { t =>
          render(
            t.dependency.module,
            t.dependency.versionConstraint,
            t.excluded,
            resolution.retainedVersions.get(t.dependency.module),
            colors0
          )
        }
    }
  }

  private def render(
    module: Module,
    version: VersionConstraint,
    excluded: Boolean,
    retainedVersionOpt: Option[Version],
    colors: Colors
  ): String =
    if (excluded)
      retainedVersionOpt match {
        case None =>
          s"${colors.yellow}(excluded)${colors.reset} $module:${version.asString}"
        case Some(version0) =>
          val versionMsg =
            if (version0.asString == version.asString)
              "this version"
            else
              s"version ${version0.asString}"

          s"$module:$version " +
            s"${colors.red}(excluded, $versionMsg present anyway)${colors.reset}"
      }
    else {
      assert(
        retainedVersionOpt.nonEmpty,
        s"No retained version found for non-excluded dependency $module"
      )
      val retainedVersion = retainedVersionOpt.get
      val versionStr      =
        if (retainedVersionOpt.forall(_.asString == version.asString))
          version.asString
        else {
          val assumeCompatibleVersions =
            compatibleVersions(version, retainedVersion)

          (if (assumeCompatibleVersions) colors.yellow else colors.red) +
            s"${version.asString} -> ${retainedVersion.asString}" +
            (if (assumeCompatibleVersions) "" else " (possible incompatibility)") +
            colors.reset
        }

      s"$module:$versionStr"
    }

  private def aligned(l: Seq[(String, String)]): Seq[String] =
    if (l.isEmpty)
      Nil
    else {
      val m = l.iterator.map(_._1.length).max
      l.map {
        case (a, b) =>
          a + " " * (m - a.length + 1) + b
      }
    }

  def conflicts(conflicts: Seq[Conflict]): Seq[String] = {

    // for deterministic order in the output
    val indices = conflicts
      .map(_.module)
      .zipWithIndex
      .reverse
      .toMap

    conflicts
      .groupBy(_.module)
      .toSeq
      .sortBy {
        case (mod, _) =>
          indices(mod)
      }
      .map {
        case (mod, l) =>
          assert(l.map(_.version0).distinct.size == 1)

          val messages = l.map { c =>
            val extra =
              if (c.wasExcluded)
                " (and excluded it)"
              else
                ""
            (
              s"${c.dependeeModule}:${c.dependeeVersionConstraint.asString}",
              s"wanted version ${c.wantedVersionConstraint.asString}" + extra
            )
          }

          s"$mod:${l.head.version0.asString} was selected, but" + System.lineSeparator() +
            aligned(messages).map("  " + _ + System.lineSeparator()).mkString
      }
  }

}
