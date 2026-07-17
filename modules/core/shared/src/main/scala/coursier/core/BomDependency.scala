package coursier.core

import coursier.version.{VersionConstraint => VersionConstraint0}
import scala.annotation.unroll

final case class BomDependency(
  module: Module,
  versionConstraint: VersionConstraint0,
  config: Configuration = Configuration.empty,
  @unroll
  forceOverrideVersions: Boolean = false
) {
  @deprecated("Use versionConstraint instead", "2.1.25")
  def version: String =
    versionConstraint.asString
  @deprecated("Use withVersionConstraint instead", "2.1.25")
  def withVersion(newVersion: String): BomDependency =
    if (newVersion == version) this
    else copy(versionConstraint = VersionConstraint0(newVersion))

  def repr: String = {
    val base = s"${module.repr}:${versionConstraint.asString}"
    val withConfig =
      if (config.isEmpty) base
      else s"$base:${config.value}"
    if (forceOverrideVersions) s"$withConfig,forceOverrideVersions"
    else withConfig
  }

  lazy val moduleVersionConstraint: (Module, VersionConstraint0) =
    (module, versionConstraint)

  @deprecated("Use moduleVersionConstraint instead", "2.1.25")
  lazy val moduleVersion: (Module, String) =
    (module, versionConstraint.asString)
}

object BomDependency {

  def create(
    module: Module,
    version: String,
    config: Configuration,
    forceOverrideVersions: Boolean
  ): BomDependency = BomDependency(
    module,
    VersionConstraint0(version),
    config,
    forceOverrideVersions
  )
  def create(
    module: Module,
    version: String,
    config: Configuration
  ): BomDependency = BomDependency(
    module,
    VersionConstraint0(version),
    config
  )
  def create(
    module: Module,
    version: String
  ): BomDependency = BomDependency(
    module,
    VersionConstraint0(version)
  )
}
