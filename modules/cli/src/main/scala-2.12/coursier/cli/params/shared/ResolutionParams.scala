package coursier.cli.params.shared

import coursier.core.Module

final case class ResolutionParams(
  keepOptionalDependencies: Boolean,
  maxIterations: Int,
  forceVersion: Map[Module, String],
  forcedProperties: Map[String, String],
  profiles: Set[String],
  typelevel: Boolean
)
