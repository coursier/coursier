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
  @since("2.1.17")
  @deprecated("Use overridesMap instead", "2.1.23")
  overrides: DependencyManagement.Map =
    Map.empty,
  @since("2.1.18")
  @deprecated("Use bomDependencies instead", "2.1.19")
  boms: Seq[(Module, String)] = Nil,
  @since("2.1.19")
  bomDependencies: Seq[BomDependency] = Nil,
  @since("2.1.23")
  overridesMap: Overrides =
    Overrides.empty
) {
  assertValid(version, "version")
  lazy val moduleVersion = (module, version)

  def asBomDependency: BomDependency =
    BomDependency(module, version, configuration)

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

  def addBom(bomDep: BomDependency): Dependency =
    withBomDependencies(bomDependencies :+ bomDep)
  def addBom(module: Module, version: String): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(module, version, Configuration.empty))
  def addBom(module: Module, version: String, config: Configuration): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(module, version, config))
  def addBoms(boms: Seq[(Module, String)]): Dependency =
    withBomDependencies(
      this.bomDependencies ++
        boms.map(t => BomDependency(t._1, t._2, Configuration.empty))
    )
  def addBomDependencies(bomDependencies: Seq[BomDependency]): Dependency =
    withBomDependencies(this.bomDependencies ++ bomDependencies)

  def addOverride(key: DependencyManagement.Key, values: DependencyManagement.Values): Dependency =
    withOverridesMap(
      Overrides.add(overridesMap, Overrides(Map(key -> values)))
    )
  def addOverrides(
    entries: Seq[(DependencyManagement.Key, DependencyManagement.Values)]
  ): Dependency =
    withOverridesMap(
      Overrides.add(
        overridesMap,
        Overrides(DependencyManagement.add(Map.empty, entries))
      )
    )
  def addOverrides(newOverrides: Overrides): Dependency =
    withOverridesMap(Overrides.add(overridesMap, newOverrides))

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
    transitive,
    Map.empty,
    boms,
    bomDependencies,
    overridesMap
  )

  lazy val clearExclusions: Dependency =
    if (minimizedExclusions.isEmpty) this
    else withMinimizedExclusions(MinimizedExclusions.zero)
  lazy val clearOverrides: Dependency =
    if (overridesMap.isEmpty) this
    else withOverridesMap(Overrides.empty)
  lazy val clearVersion: Dependency =
    if (version.isEmpty) this
    else withVersion("")
  lazy val depManagementKey: DependencyManagement.Key =
    DependencyManagement.Key(
      module.organization,
      module.name,
      if (publication.`type`.isEmpty) Type.jar else publication.`type`,
      publication.classifier
    )
  lazy val hasProperties =
    module.hasProperties ||
    version.contains("$") ||
    publication.attributesHaveProperties ||
    configuration.value.contains("$") ||
    minimizedExclusions.hasProperties

  // Overriding toString to be backwards compatible with Set-based exclusion representation
  override def toString(): String = {
    var fields = Seq(
      module.toString,
      version.toString,
      configuration.toString,
      minimizedExclusions.toSet().toString,
      publication.toString,
      optional.toString,
      transitive.toString
    )
    fields =
      if (overridesMap.isEmpty) fields
      else fields :+ overridesMap.flatten.toMap.toString
    fields =
      if (boms.isEmpty) fields
      else fields :+ boms.toString
    fields =
      if (bomDependencies.isEmpty) fields
      else fields :+ bomDependencies.toString
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
    overrides: DependencyManagement.Map,
    boms: Seq[(Module, String)],
    bomDependencies: Seq[BomDependency],
    overridesMap: Overrides
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
        overrides,
        boms,
        bomDependencies,
        overridesMap
      )
    )

  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map,
    boms: Seq[(Module, String)],
    bomDependencies: Seq[BomDependency]
  ): Dependency =
    Dependency(
      module,
      version,
      configuration,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty,
      boms,
      bomDependencies,
      Overrides(overrides)
    )

  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map,
    boms: Seq[(Module, String)]
  ): Dependency =
    Dependency(
      module,
      version,
      configuration,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty,
      boms,
      Nil,
      Overrides(overrides)
    )

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
    Dependency(
      module,
      version,
      configuration,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty,
      Nil,
      Nil,
      Overrides(overrides)
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
      Map.empty,
      Nil,
      Nil,
      Overrides.empty
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
