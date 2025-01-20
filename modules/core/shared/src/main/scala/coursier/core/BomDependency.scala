package coursier.core

import coursier.version.{VersionConstraint => VersionConstraint0}
import dataclass.data

@data class BomDependency(
  module: Module,
  versionConstraint: VersionConstraint0,
  config: Configuration = Configuration.empty,
  @since
  forceOverrideVersions: Boolean = false
) {
  lazy val moduleVersionConstraint: (Module, VersionConstraint0) =
    (module, versionConstraint)
}
