package coursier.cli.params.shared

import coursier.core.{Configuration, Dependency, Module, ModuleName, Organization}

final case class ResolutionParams(
  keepOptionalDependencies: Boolean,
  maxIterations: Int,
  forceVersion: Map[Module, String],
  forcedProperties: Map[String, String],
  exclude: Set[(Organization, ModuleName)],
  perModuleExclude: Map[String, Set[(Organization, ModuleName)]], // FIXME key should be Module
  scalaVersion: String,
  intransitiveDependencies: Seq[(Dependency, Map[String, String])],
  sbtPluginDependencies: Seq[(Dependency, Map[String, String])],
  defaultConfiguration: Configuration,
  profiles: Set[String],
  typelevel: Boolean
)
