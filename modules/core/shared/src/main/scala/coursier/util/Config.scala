package coursier.util

import coursier.core.{Configuration, Dependency, Resolution, VariantSelector}

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

  @deprecated(
    "Unused, behavior uncertain since the introduction of Variant and VariantSelector",
    "2.1.25"
  )
  def dependenciesWithConfig(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Set[Dependency] =
    allDependenciesByConfig(res, depsByConfig, configs)
      .flatMap {
        case (config, deps) =>
          deps.map { dep =>
            dep.variantSelector match {
              case c: VariantSelector.ConfigurationBased =>
                dep.withVariantSelector(
                  VariantSelector.ConfigurationBased(config --> c.configuration)
                )
            }
          }
      }
      .groupBy(_.withVariantSelector(VariantSelector.emptyConfiguration))
      .flatMap {
        case (dep, l) =>
          val configBased = l.map { dep0 =>
            dep0.variantSelector match {
              case c: VariantSelector.ConfigurationBased =>
                c.configuration
            }
          }
          if (configBased.isEmpty) Nil
          else
            Seq(
              dep.withVariantSelector(
                VariantSelector.ConfigurationBased(
                  Configuration.join(configBased.toVector.distinct.sorted: _*)
                )
              )
            )
      }
      .toSet

}
