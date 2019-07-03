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

  val publication: Publication,

  // Maven-specific
  val optional: Boolean,

  val transitive: Boolean
) {
  lazy val moduleVersion = (module, version)

  private def copy0(
    module: Module = module,
    version: String = version,
    configuration: Configuration = configuration,
    exclusions: Set[(Organization, ModuleName)] = exclusions,
    publication: Publication = publication,
    optional: Boolean = optional,
    transitive: Boolean = transitive
  ): Dependency =
    new Dependency(
      module,
      version,
      configuration,
      exclusions,
      publication,
      optional,
      transitive
    )

  @deprecated("Use the with* methods instead", "2.0.0-RC3")
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
      publication = publication.copy(
        `type` = attributes.`type`,
        classifier = attributes.classifier
      ),
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
          publication == other.publication &&
          optional == other.optional &&
          transitive == other.transitive
      case _ => false
    }

  override lazy val hashCode: Int = {
    var code = 17 + "coursier.core.Dependency".##
    code = 37 * code + module.##
    code = 37 * code + version.##
    code = 37 * code + configuration.##
    code = 37 * code + exclusions.##
    code = 37 * code + publication.##
    code = 37 * code + optional.##
    code = 37 * code + transitive.##
    code
  }

  override def toString: String =
    s"Dependency($module, $version, $configuration, $exclusions, $publication, $optional, $transitive)"

  def mavenPrefix: String =
    if (attributes.isEmpty)
      module.orgName
    else
      s"${module.orgName}:${attributes.packagingAndClassifier}"

  def attributes: Attributes =
    publication.attributes

  def withModule(module: Module): Dependency =
    copy0(module = module)
  def withVersion(version: String): Dependency =
    copy0(version = version)
  def withConfiguration(configuration: Configuration): Dependency =
    copy0(configuration = configuration)
  def withExclusions(exclusions: Set[(Organization, ModuleName)]): Dependency =
    copy0(exclusions = exclusions)
  def withAttributes(attributes: Attributes): Dependency =
    copy0(publication = publication.copy(
      `type` = attributes.`type`,
      classifier = attributes.classifier
    ))
  def withPublication(publication: Publication): Dependency =
    copy0(publication = publication)
  def withPublication(name: String): Dependency =
    copy0(publication = Publication(name, Type.empty, Extension.empty, Classifier.empty))
  def withPublication(name: String, `type`: Type): Dependency =
    copy0(publication = Publication(name, `type`, Extension.empty, Classifier.empty))
  def withPublication(name: String, `type`: Type, ext: Extension): Dependency =
    copy0(publication = Publication(name, `type`, ext, Classifier.empty))
  def withPublication(name: String, `type`: Type, ext: Extension, classifier: Classifier): Dependency =
    copy0(publication = Publication(name, `type`, ext, classifier))
  def withOptional(optional: Boolean): Dependency =
    copy0(optional = optional)
  def withTransitive(transitive: Boolean): Dependency =
    copy0(transitive = transitive)

  lazy val clearExclusions: Dependency =
    withExclusions(Set.empty)
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
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    new Dependency(
      module,
      version,
      configuration,
      exclusions,
      publication,
      optional,
      transitive
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
      Publication("", attributes.`type`, Extension.empty, attributes.classifier),
      optional,
      transitive
    )

}
