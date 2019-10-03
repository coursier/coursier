package coursier.core

import dataclass.data

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
@data class Dependency(
  module: Module,
  version: String,
  configuration: Configuration,
  exclusions: Set[(Organization, ModuleName)],

  publication: Publication,

  // Maven-specific
  optional: Boolean,

  transitive: Boolean
) {
  lazy val moduleVersion = (module, version)

  def mavenPrefix: String =
    if (attributes.isEmpty)
      module.orgName
    else
      s"${module.orgName}:${attributes.packagingAndClassifier}"

  def attributes: Attributes =
    publication.attributes

  def withAttributes(attributes: Attributes): Dependency =
    withPublication(publication.withType(attributes.`type`).withClassifier(attributes.classifier))
  def withPublication(name: String): Dependency =
    withPublication(Publication(name, Type.empty, Extension.empty, Classifier.empty))
  def withPublication(name: String, `type`: Type): Dependency =
    withPublication(Publication(name, `type`, Extension.empty, Classifier.empty))
  def withPublication(name: String, `type`: Type, ext: Extension): Dependency =
    withPublication(Publication(name, `type`, ext, Classifier.empty))
  def withPublication(name: String, `type`: Type, ext: Extension, classifier: Classifier): Dependency =
    withPublication(Publication(name, `type`, ext, classifier))

  lazy val clearExclusions: Dependency =
    withExclusions(Set.empty)

  override lazy val hashCode: Int =
    tuple.hashCode()
}

object Dependency {

  def apply(
    module: Module,
    version: String
  ): Dependency =
    Dependency(
      module,
      version,
      Configuration.empty,
      Set.empty[(Organization, ModuleName)],
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
    Dependency(
      module,
      version,
      configuration,
      exclusions,
      Publication("", attributes.`type`, Extension.empty, attributes.classifier),
      optional,
      transitive
    )

}
