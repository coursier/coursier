package coursier.core

import java.util

import dataclass.data

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
@data(apply = false) class Dependency(
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

  //is necessary to hold a strong ref `key` field as it will be used as WeakKeys in memoised_cache
  private[core] val key = tuple

  lazy val clearExclusions: Dependency =
    withExclusions(Set.empty)

  override lazy val hashCode: Int =
    key.hashCode()
}

object Dependency {

  private[core] val memoised_cache: java.util.WeakHashMap[(Module, String, Configuration, Set[(Organization, ModuleName)], Publication, Boolean, Boolean), java.lang.ref.WeakReference[Dependency]] =
    coursier.util.Cache.createCache()

  def apply(module: Module, version: String, configuration: Configuration, exclusions: Set[(Organization, ModuleName)], publication: Publication, optional: Boolean, transitive: Boolean): Dependency = {
    coursier.util.Cache.xpto(memoised_cache)(
      (module, version, configuration, exclusions, publication, optional, transitive),
      _ => new Dependency(module, version, configuration, exclusions, publication, optional, transitive),
      _.key
    )
  }


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
