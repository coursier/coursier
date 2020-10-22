package coursier.core

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
    new java.util.WeakHashMap[(Module, String, Configuration, Set[(Organization, ModuleName)], Publication, Boolean, Boolean), java.lang.ref.WeakReference[Dependency]]()

  def apply(module: Module, version: String, configuration: Configuration, exclusions: Set[(Organization, ModuleName)], publication: Publication, optional: Boolean, transitive: Boolean): Dependency = {
    val key = (module, version, configuration, exclusions, publication, optional, transitive)
    val first = {
      val weak = memoised_cache.get(key)
      if (weak == null) null else weak.get
    }
    if (first != null) {
      first
    } else {
      memoised_cache.synchronized {
        val got = {
          val weak = memoised_cache.get(key)
          if (weak == null) {
            null
          } else {
            val ref = weak.get
            ref
          }
        }
        if (got != null) {
          got
        } else {
          val created = new Dependency(module, version, configuration, exclusions, publication, optional, transitive)
          //it is important to use created.key as the key in WeakHashMap
          memoised_cache.put(created.key, new _root_.java.lang.ref.WeakReference(created))
          created
        }
      }
    }
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
