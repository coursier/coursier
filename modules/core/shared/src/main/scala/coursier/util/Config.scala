package coursier.util

import coursier.core.{Configuration, Dependency, Resolution}

object Config {

  // loose attempt at minimizing a set of dependencies from various configs
  // `configs` is assumed to be fully unfold
  def allDependenciesByConfig(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Map[Configuration, Set[Dependency]] = {

    val allDepsByConfig = depsByConfig.map {
      case (config, deps) =>
        config -> res.subset(deps.toVector).minDependencies
    }

    val filteredAllDepsByConfig = allDepsByConfig.map {
      case (config, allDeps) =>
        val allExtendedConfigs = configs.getOrElse(config, Set.empty) - config
        val inherited = allExtendedConfigs
          .flatMap(allDepsByConfig.getOrElse(_, Set.empty))

        config -> (allDeps -- inherited)
    }

    filteredAllDepsByConfig
  }

  def dependenciesWithConfig(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Set[Dependency] =
    allDependenciesByConfig(res, depsByConfig, configs)
      .flatMap {
        case (config, deps) =>
          deps.map(dep => dep.withConfiguration(config --> dep.configuration))
      }
      .groupBy(_.withConfiguration(Configuration.empty))
      .map {
        case (dep, l) =>
          dep.withConfiguration(Configuration.join(l.map(_.configuration).toSeq.distinct.sorted: _*))
      }
      .toSet

}
