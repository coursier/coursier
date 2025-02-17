package coursier.util

import coursier.core.{Configuration, Dependency, Resolution, VariantSelector}
import coursier.error.DependencyError

@deprecated("Unused by coursier", "2.1.25")
object Config {

  // loose attempt at minimizing a set of dependencies from various configs
  // `configs` is assumed to be fully unfold
  def allDependenciesByConfig0(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Either[DependencyError, Map[Configuration, Set[Dependency]]] = {

    val allDepsByConfigOrErrors = depsByConfig.toSeq.map {
      case (config, deps) =>
        res.subset0(deps.toVector)
          .map(_.minDependencies)
          .map(config -> _)
    }

    val errors = allDepsByConfigOrErrors.collect {
      case Left(err) => err
    }

    errors match {
      case Seq() =>
        val allDepsByConfig = allDepsByConfigOrErrors
          .collect {
            case Right(kv) => kv
          }
          .toMap

        val filteredAllDepsByConfig = allDepsByConfig.map {
          case (config, allDeps) =>
            val allExtendedConfigs = configs.getOrElse(config, Set.empty) - config
            val inherited = allExtendedConfigs
              .flatMap(allDepsByConfig.getOrElse(_, Set.empty))

            config -> (allDeps -- inherited)
        }

        Right(filteredAllDepsByConfig)

      case Seq(first, others @ _*) =>
        for (other <- others)
          first.addSuppressed(other)
        Left(first)
    }
  }

  @deprecated("Use allDependenciesByConfig0 instead", "2.1.25")
  def allDependenciesByConfig(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Map[Configuration, Set[Dependency]] =
    allDependenciesByConfig0(res, depsByConfig, configs)
      .toTry.get

  @deprecated(
    "Unused, behavior uncertain since the introduction of Variant and VariantSelector",
    "2.1.25"
  )
  def dependenciesWithConfig(
    res: Resolution,
    depsByConfig: Map[Configuration, Set[Dependency]],
    configs: Map[Configuration, Set[Configuration]]
  ): Set[Dependency] =
    allDependenciesByConfig0(res, depsByConfig, configs)
      .left.map { ex =>
        throw new Exception("Deprecated method can't handle error", ex)
      }
      .merge
      .flatMap {
        case (config, deps) =>
          deps.map { dep =>
            dep.variantSelector match {
              case c: VariantSelector.ConfigurationBased =>
                dep.withVariantSelector(
                  VariantSelector.ConfigurationBased(config --> c.configuration)
                )
              case _: VariantSelector.AttributesBased =>
                dep
            }
          }
      }
      .groupBy(_.withVariantSelector(VariantSelector.emptyConfiguration))
      .flatMap {
        case (dep, l) =>
          val split = l.map { dep0 =>
            dep0.variantSelector match {
              case c: VariantSelector.ConfigurationBased =>
                Left(c.configuration)
              case _: VariantSelector.AttributesBased =>
                Right(dep0)
            }
          }
          val configBased = split.collect {
            case Left(conf) => conf
          }
          val others = split.collect {
            case Right(dep0) => dep0
          }
          val updatedConfBased =
            if (configBased.isEmpty) Nil
            else
              Seq(
                dep.withVariantSelector(
                  VariantSelector.ConfigurationBased(
                    Configuration.join(configBased.toVector.distinct.sorted: _*)
                  )
                )
              )
          updatedConfBased ++ others
      }
      .toSet

}
