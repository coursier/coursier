package coursier.core

object Orders {

  @deprecated("Will likely be removed at some point in future versions", "2.0.0-RC3")
  trait PartialOrdering[T] extends scala.math.PartialOrdering[T] {
    def lteq(x: T, y: T): Boolean =
      tryCompare(x, y)
        .exists(_ <= 0)
  }

  /** All configurations that each configuration extends, including the ones it extends transitively */
  @deprecated("Will likely be removed at some point in future versions", "2.0.0-RC3")
  def allConfigurations(configurations: Map[Configuration, Seq[Configuration]]): Map[Configuration, Set[Configuration]] = {
    def allParents(config: Configuration): Set[Configuration] = {
      def helper(configs: Set[Configuration], acc: Set[Configuration]): Set[Configuration] =
        if (configs.isEmpty)
          acc
        else if (configs.exists(acc))
          helper(configs -- acc, acc)
        else if (configs.exists(!configurations.contains(_))) {
          val (remaining, notFound) = configs.partition(configurations.contains)
          helper(remaining, acc ++ notFound)
        } else {
          val extraConfigs = configs.flatMap(configurations)
          helper(extraConfigs, acc ++ configs)
        }

      helper(Set(config), Set.empty)
    }

    configurations
      .keys
      .toList
      .map(config => config -> (allParents(config) - config))
      .toMap
  }

  /**
    * Configurations partial order based on configuration mapping `configurations`.
    *
    * @param configurations: for each configuration, the configurations it directly extends.
    */
  @deprecated("Will likely be removed at some point in future versions", "2.0.0-RC3")
  def configurationPartialOrder(configurations: Map[Configuration, Seq[Configuration]]): PartialOrdering[Configuration] =
    new PartialOrdering[Configuration] {
      val allParentsMap = allConfigurations(configurations)

      def tryCompare(x: Configuration, y: Configuration) =
        if (x == y)
          Some(0)
        else if (allParentsMap.get(x).exists(_(y)))
          Some(-1)
        else if (allParentsMap.get(y).exists(_(x)))
          Some(1)
        else
          None
    }

  /** Non-optional < optional */
  @deprecated("Will likely be removed at some point in future versions", "2.0.0-RC3")
  val optionalPartialOrder: PartialOrdering[Boolean] =
    new PartialOrdering[Boolean] {
      def tryCompare(x: Boolean, y: Boolean) =
        Some(
          if (x == y) 0
          else if (x) 1
          else -1
        )
    }

  /**
   * Exclusions partial order.
   *
   * x <= y iff all that x excludes is also excluded by y.
   * x and y not related iff x excludes some elements not excluded by y AND
   *                         y excludes some elements not excluded by x.
   *
   * In particular, no exclusions <= anything <= Set(("*", "*"))
   */
  @deprecated("Can give incorrect results - will likely be removed at some point in future versions", "2.0.0-RC3")
  val exclusionsPartialOrder: PartialOrdering[Set[(Organization, ModuleName)]] =
    new PartialOrdering[Set[(Organization, ModuleName)]] {
      def boolCmp(a: Boolean, b: Boolean) = (a, b) match {
        case (true, true) => Some(0)
        case (true, false) => Some(1)
        case (false, true) => Some(-1)
        case (false, false) => None
      }

      def tryCompare(x: Set[(Organization, ModuleName)], y: Set[(Organization, ModuleName)]) = {
        val (xAll, xExcludeByOrg1, xExcludeByName1, xRemaining0) = Exclusions.partition(x)
        val (yAll, yExcludeByOrg1, yExcludeByName1, yRemaining0) = Exclusions.partition(y)

        boolCmp(xAll, yAll).orElse {
          def filtered(e: Set[(Organization, ModuleName)]) =
            e.filter{case (org, name) =>
              !xExcludeByOrg1(org) && !yExcludeByOrg1(org) &&
                !xExcludeByName1(name) && !yExcludeByName1(name)
            }

          def removeIntersection[T](a: Set[T], b: Set[T]) =
            (a -- b, b -- a)

          def allEmpty(set: Set[_]*) = set.forall(_.isEmpty)

          val (xRemaining1, yRemaining1) =
            (filtered(xRemaining0), filtered(yRemaining0))

          val (xProperRemaining, yProperRemaining) =
            removeIntersection(xRemaining1, yRemaining1)

          val (onlyXExcludeByOrg, onlyYExcludeByOrg) =
            removeIntersection(xExcludeByOrg1, yExcludeByOrg1)

          val (onlyXExcludeByName, onlyYExcludeByName) =
            removeIntersection(xExcludeByName1, yExcludeByName1)

          val (noXProper, noYProper) = (
            allEmpty(xProperRemaining, onlyXExcludeByOrg, onlyXExcludeByName),
            allEmpty(yProperRemaining, onlyYExcludeByOrg, onlyYExcludeByName)
          )

          boolCmp(noYProper, noXProper) // order matters
        }
      }
    }

  private def fallbackConfigIfNecessary(dep: Dependency, configs: Set[Configuration]): Dependency =
    Parse.withFallbackConfig(dep.configuration) match {
      case Some((main, fallback)) =>
        val config0 =
          if (configs(main))
            main
          else if (configs(fallback))
            fallback
          else
            dep.configuration

        dep.withConfiguration(config0)
      case _ =>
        dep
    }

  /**
   * Assume all dependencies have same `module`, `version`, and `artifact`; see `minDependencies`
   * if they don't.
   */
  @deprecated("Can give incorrect results - will likely be removed at some point in future versions", "2.0.0-RC3")
  def minDependenciesUnsafe(
    dependencies: Set[Dependency],
    configs: Map[Configuration, Seq[Configuration]]
  ): Set[Dependency] = {
    val availableConfigs = configs.keySet
    val groupedDependencies = dependencies
      .map(fallbackConfigIfNecessary(_, availableConfigs))
      .groupBy(dep => (dep.optional, dep.configuration))
      .mapValues(deps => deps.head.withExclusions(deps.foldLeft(Exclusions.one)((acc, dep) => Exclusions.meet(acc, dep.exclusions))))
      .toList

    val remove =
      for {
        List(((xOpt, xScope), xDep), ((yOpt, yScope), yDep)) <- groupedDependencies.combinations(2)
        optCmp <- optionalPartialOrder.tryCompare(xOpt, yOpt).iterator
        scopeCmp <- configurationPartialOrder(configs).tryCompare(xScope, yScope).iterator
        if optCmp*scopeCmp >= 0
        exclCmp <- exclusionsPartialOrder.tryCompare(xDep.exclusions, yDep.exclusions).iterator
        if optCmp*exclCmp >= 0
        if scopeCmp*exclCmp >= 0
        xIsMin = optCmp < 0 || scopeCmp < 0 || exclCmp < 0
        yIsMin = optCmp > 0 || scopeCmp > 0 || exclCmp > 0
        if xIsMin || yIsMin // should be always true, unless xDep == yDep, which shouldn't happen
      } yield if (xIsMin) yDep else xDep

    groupedDependencies.map(_._2).toSet -- remove
  }

  /**
   * Minified representation of `dependencies`.
   *
   * The returned set brings exactly the same things as `dependencies`, with no redundancy.
   */
  @deprecated("Can give incorrect results - will likely be removed at some point in future versions, use DependencySet.minimizedSet instead to minimize a dependency set", "2.0.0-RC3")
  def minDependencies(
    dependencies: Set[Dependency],
    configs: ((Module, String)) => Map[Configuration, Seq[Configuration]]
  ): Set[Dependency] = {
    dependencies
      .groupBy(_.withConfiguration(Configuration.empty).withExclusions(Set.empty).withOptional(false))
      .mapValues(deps => minDependenciesUnsafe(deps, configs(deps.head.moduleVersion)))
      .valuesIterator
      .fold(Set.empty)(_ ++ _)
  }

}
