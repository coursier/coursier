package coursier.core

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
final class Dependency private (
  val module: Module,
  val version: String,
  val configuration: Configuration,
  val exclusions: Set[(Organization, ModuleName)],

  // Maven-specific
  val attributes: Attributes,
  val optional: Boolean,

  val transitive: Boolean
) {
  lazy val moduleVersion = (module, version)

  private def copy0(
    module: Module = module,
    version: String = version,
    configuration: Configuration = configuration,
    exclusions: Set[(Organization, ModuleName)] = exclusions,
    attributes: Attributes = attributes,
    optional: Boolean = optional,
    transitive: Boolean = transitive
  ): Dependency =
    new Dependency(
      module,
      version,
      configuration,
      exclusions,
      attributes,
      optional,
      transitive
    )

  @deprecated("2.0.0-RC3", "Use the with* methods instead")
  def copy(
    module: Module = module,
    version: String = version,
    configuration: Configuration = configuration,
    exclusions: Set[(Organization, ModuleName)] = exclusions,
    attributes: Attributes = attributes,
    optional: Boolean = optional,
    transitive: Boolean = transitive
  ): Dependency =
    copy0(
      module = module,
      version = version,
      configuration = configuration,
      exclusions = exclusions,
      attributes = attributes,
      optional = optional,
      transitive = transitive
    )

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Dependency =>
        module == other.module &&
          version == other.version &&
          configuration == other.configuration &&
          exclusions == other.exclusions &&
          attributes == other.attributes &&
          optional == other.optional &&
          transitive == other.transitive
      case _ => false
    }

  override lazy val hashCode: Int = {
    var code = 17 + "coursier.code.Dependency".##
    code = 37 * code + module.##
    code = 37 * code + version.##
    code = 37 * code + configuration.##
    code = 37 * code + exclusions.##
    code = 37 * code + attributes.##
    code = 37 * code + optional.##
    code = 37 * code + transitive.##
    code
  }

  override def toString: String =
    s"Dependency($module, $version, $configuration, $exclusions, $attributes, $optional, $transitive)"

  def mavenPrefix: String = {
    if (attributes.isEmpty)
      module.orgName
    else {
      s"${module.orgName}:${attributes.packagingAndClassifier}"
    }
  }

  def withModule(module: Module): Dependency =
    copy(module = module)
  def withVersion(version: String): Dependency =
    copy(version = version)
  def withConfiguration(configuration: Configuration): Dependency =
    copy(configuration = configuration)
  def withExclusions(exclusions: Set[(Organization, ModuleName)]): Dependency =
    copy(exclusions = exclusions)
  def withAttributes(attributes: Attributes): Dependency =
    copy(attributes = attributes)
  def withOptional(optional: Boolean): Dependency =
    copy(optional = optional)
  def withTransitive(transitive: Boolean): Dependency =
    copy(transitive = transitive)
}

object Dependency {

  def apply(
    module: Module,
    version: String
  ): Dependency =
    new Dependency(
      module,
      version,
      Configuration.empty,
      Set.empty,
      Publication("", Type.empty, Extension.empty, Classifier.empty),
      optional = false,
      transitive = true
    )

  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    exclusions: Set[(Organization, ModuleName)],
    attributes: Attributes,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    new Dependency(
      module,
      version,
      configuration,
      exclusions,
      attributes,
      optional,
      transitive
    )

}
