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
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    config: Configuration,
    forceOverrideVersions: Boolean
  ) = this(
    module,
    VersionConstraint0(version),
    config,
    forceOverrideVersions
  )
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def this(
    module: Module,
    version: String,
    config: Configuration
  ) = this(
    module,
    VersionConstraint0(version),
    config
  )
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def this(
    module: Module,
    version: String
  ) = this(
    module,
    VersionConstraint0(version)
  )

  @deprecated("Use versionConstraint instead", "2.1.25")
  def version: String =
    versionConstraint.asString
  @deprecated("Use withVersionConstraint instead", "2.1.25")
  def withVersion(newVersion: String): BomDependency =
    if (newVersion == version) this
    else withVersionConstraint(VersionConstraint0(newVersion))

  lazy val moduleVersionConstraint: (Module, VersionConstraint0) =
    (module, versionConstraint)

  @deprecated("Use moduleVersionConstraint instead", "2.1.25")
  lazy val moduleVersion: (Module, String) =
    (module, versionConstraint.asString)
}

object BomDependency {

  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    config: Configuration,
    forceOverrideVersions: Boolean
  ): BomDependency = apply(
    module,
    VersionConstraint0(version),
    config,
    forceOverrideVersions
  )
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def apply(
    module: Module,
    version: String,
    config: Configuration
  ): BomDependency = apply(
    module,
    VersionConstraint0(version),
    config
  )
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def apply(
    module: Module,
    version: String
  ): BomDependency = apply(
    module,
    VersionConstraint0(version)
  )
}
