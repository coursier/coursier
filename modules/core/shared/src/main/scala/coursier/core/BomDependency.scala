package coursier.core

import dataclass.data

@data class BomDependency(
  module: Module,
  version: String,
  config: Configuration = Configuration.empty,
  @since
  forceOverrideVersions: Boolean = false
) {
  lazy val moduleVersion: (Module, String) =
    (module, version)
}
