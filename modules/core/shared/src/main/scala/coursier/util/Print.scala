package coursier.util

import coursier.core._
import coursier.graph.{Conflict, DependencyTree, ReverseModuleTree}

object Print {

  object Colors {
    private val `with`: Colors = Colors(Console.RED, Console.YELLOW, Console.RESET)
    private val `without`: Colors = Colors("", "", "")

    def get(colors: Boolean): Colors = if (colors) `with` else `without`
  }

  case class Colors private(red: String, yellow: String, reset: String)

  def dependency(dep: Dependency): String =
    dependency(dep, printExclusions = false)

  def dependency(dep: Dependency, printExclusions: Boolean): String = {

    def exclusionsStr = dep
      .exclusions
      .toVector
      .sorted
      .map {
        case (org, name) =>
          s"\n  exclude($org, $name)"
      }
      .mkString

    s"${dep.module}:${dep.version}:${dep.configuration.value}" + (if (printExclusions) exclusionsStr else "")
  }

  def dependenciesUnknownConfigs(deps: Seq[Dependency], projects: Map[(Module, String), Project]): String =
    dependenciesUnknownConfigs(deps, projects, printExclusions = false)

  def dependenciesUnknownConfigs(
    deps: Seq[Dependency],
    projects: Map[(Module, String), Project],
    printExclusions: Boolean
  ): String = {

    val deps0 = deps.map { dep =>
      dep.copy(
        version = projects
          .get(dep.moduleVersion)
          .fold(dep.version)(_.version)
      )
    }

    val minDeps = Orders.minDependencies(
      deps0.toSet,
      _ => Map.empty
    )

    val deps1 = minDeps
      .groupBy(_.copy(configuration = Configuration.empty, attributes = Attributes.empty))
      .toVector
      .map { case (k, l) =>
        k.copy(configuration = Configuration.join(l.toVector.map(_.configuration).sorted.distinct: _*))
      }
      .sortBy { dep =>
        (dep.module.organization, dep.module.name, dep.module.toString, dep.version)
      }

    deps1.map(dependency(_, printExclusions)).mkString("\n")
  }

  def compatibleVersions(first: String, second: String): Boolean = {
    // too loose for now
    // e.g. RCs and milestones should not be considered compatible with subsequent non-RC or
    // milestone versions - possibly not with each other either

    first.split('.').take(2).toSeq == second.split('.').take(2).toSeq
  }

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
        roots0.map(_.module),
        DependencyTree(resolution, withExclusions = printExclusions)
      )

      Tree(t.toVector.sortBy(t => (t.module.organization.value, t.module.name.value, t.module.nameWithAttributes)))(_.dependees)
        .render { node =>
          if (node.excludedDependsOn)
            s"${colors0.yellow}(excluded by)${colors0.reset} ${node.module}:${node.reconciledVersion}"
          else if (node.dependsOnVersion == node.dependsOnReconciledVersion)
            s"${node.module}:${node.reconciledVersion}"
          else {
            val assumeCompatibleVersions = compatibleVersions(node.dependsOnVersion, node.dependsOnReconciledVersion)

            s"${node.module}:${node.reconciledVersion} " +
              (if (assumeCompatibleVersions) colors0.yellow else colors0.red) +
              s"${node.dependsOnModule}:${node.dependsOnVersion} -> ${node.dependsOnReconciledVersion}" +
              colors0.reset
          }
        }
    } else {
      val roots0 = Option(roots).getOrElse(resolution.rootDependencies)
      val t = DependencyTree(resolution, roots0, withExclusions = printExclusions)
      Tree(t.toVector)(_.children)
        .render { t =>
          render(
            t.dependency.module,
            t.dependency.version,
            t.excluded,
            resolution.reconciledVersions.get(t.dependency.module),
            colors0
          )
        }
    }
  }

  private def render(
    module: Module,
    version: String,
    excluded: Boolean,
    reconciledVersionOpt: Option[String],
    colors: Colors
  ): String =
    if (excluded)
      reconciledVersionOpt match {
        case None =>
          s"${colors.yellow}(excluded)${colors.reset} $module:$version"
        case Some(version0) =>
          val versionMsg =
            if (version0 == version)
              "this version"
            else
              s"version $version0"

          s"$module:$version " +
            s"${colors.red}(excluded, $versionMsg present anyway)${colors.reset}"
      }
    else {
      val versionStr =
        if (reconciledVersionOpt.forall(_ == version))
          version
        else {
          val reconciledVersion = reconciledVersionOpt.getOrElse(version)
          val assumeCompatibleVersions = compatibleVersions(version, reconciledVersionOpt.getOrElse(version))

          (if (assumeCompatibleVersions) colors.yellow else colors.red) +
            s"$version -> $reconciledVersion" +
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
          a + " "*(m - a.length + 1) + b
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
          assert(l.map(_.version).distinct.size == 1)

          val messages = l.map { c =>
            val extra =
              if (c.wasExcluded)
                " (and excluded it)"
              else
                ""
            (s"${c.dependeeModule}:${c.dependeeVersion}", s"wanted version ${c.wantedVersion}" + extra)
          }

          s"$mod:${l.head.version} was selected, but\n" +
            aligned(messages).map("  " + _ + "\n").mkString
      }
  }

}
