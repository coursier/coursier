package coursier.core

import java.util.concurrent.ConcurrentMap

import coursier.core.Validation._
import dataclass.data
import MinimizedExclusions._

/** Dependencies with the same @module will typically see their @version-s merged.
  *
  * The remaining fields are left untouched, some being transitively propagated (exclusions,
  * optional, in particular).
  */
@data(apply = false, settersCallApply = true) class Dependency(
  module: Module,
  version: String,
  configuration: Configuration,
  minimizedExclusions: MinimizedExclusions,
  publication: Publication,
  // Maven-specific
  optional: Boolean,
  transitive: Boolean,
  @since("2.1.15")
  overrides: DependencyManagement.Map =
    Map.empty
) {
  assertValid(version, "version")
  lazy val moduleVersion = (module, version)

  def this(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: Set[(Organization, ModuleName)],
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ) = this(
    module,
    version,
    configuration,
    MinimizedExclusions(minimizedExclusions),
    publication,
    optional,
    transitive
  )

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
  def withPublication(
    name: String,
    `type`: Type,
    ext: Extension,
    classifier: Classifier
  ): Dependency =
    withPublication(Publication(name, `type`, ext, classifier))

  @deprecated(
    "This method will be dropped in favor of withMinimizedExclusions in a future version",
    "2.1.0-M6"
  )
  def withExclusions(newExclusions: Set[(Organization, ModuleName)]): Dependency =
    withMinimizedExclusions(MinimizedExclusions(newExclusions))

  @deprecated(
    "This method will be dropped in favor of minimizedExclusions() in a future version",
    "2.1.0-M6"
  )
  def exclusions(): Set[(Organization, ModuleName)] = minimizedExclusions.toSet()

  private[core] def copy(
    module: Module = this.module,
    version: String = this.version,
    configuration: Configuration = this.configuration,
    minimizedExclusions: MinimizedExclusions = this.minimizedExclusions,
    attributes: Attributes = this.attributes,
    optional: Boolean = this.optional,
    transitive: Boolean = this.transitive
  ) = Dependency(
    module,
    version,
    configuration,
    minimizedExclusions,
    Publication("", attributes.`type`, Extension.empty, attributes.classifier),
    optional,
    transitive
  )

  lazy val clearExclusions: Dependency =
    withMinimizedExclusions(MinimizedExclusions.zero)
  lazy val clearOverrides: Dependency =
    withOverrides(Map.empty)

  // Overriding toString to be backwards compatible with Set-based exclusion representation
  override def toString(): String = {
    val baseFields = Seq(
      module.toString,
      version.toString,
      configuration.toString,
      minimizedExclusions.toSet().toString,
      publication.toString,
      optional.toString,
      transitive.toString
    )
    val fields =
      if (overrides.isEmpty) baseFields
      else baseFields :+ overrides.toString
    s"Dependency(${fields.mkString(", ")})"
  }

  override lazy val hashCode: Int =
    tuple.hashCode()
}

object Dependency {

  private[coursier] val instanceCache: ConcurrentMap[Dependency, Dependency] =
    coursier.util.Cache.createCache()

  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map
  ): Dependency =
    coursier.util.Cache.cacheMethod(instanceCache)(
      new Dependency(
        module,
        version,
        configuration,
        minimizedExclusions,
        publication,
        optional,
        transitive,
        overrides
      )
    )

  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    Dependency(
      module,
      version,
      configuration,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty
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
    Dependency(
      module,
      version,
      configuration,
      MinimizedExclusions(exclusions),
      publication,
      optional,
      transitive
    )

  def apply(
    module: Module,
    version: String
  ): Dependency =
    Dependency(
      module,
      version,
      Configuration.empty,
      MinimizedExclusions.zero,
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
      MinimizedExclusions(exclusions),
      Publication("", attributes.`type`, Extension.empty, attributes.classifier),
      optional,
      transitive
    )

}
