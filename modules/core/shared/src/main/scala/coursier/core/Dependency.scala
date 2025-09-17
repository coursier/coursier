package coursier.core

import coursier.core.Validation._
import coursier.version.{VersionConstraint => VersionConstraint0}
import dataclass.{data, since}

import java.util.concurrent.ConcurrentMap

/** Dependencies with the same @module will typically see their @version-s merged.
  *
  * The remaining fields are left untouched, some being transitively propagated (exclusions,
  * optional, in particular).
  */
@data(apply = false, settersCallApply = true) class Dependency(
  module: Module,
  versionConstraint: VersionConstraint0,
  variantSelector: VariantSelector,
  minimizedExclusions: MinimizedExclusions,
  publication: Publication,
  // Maven-specific
  optional: Boolean,
  transitive: Boolean,
  @since("2.1.17")
  @deprecated("Use overridesMap instead", "2.1.23")
  overrides: DependencyManagement.Map =
    Map.empty[DependencyManagement.Key, DependencyManagement.Values],
  @since("2.1.18")
  @deprecated("Use bomDependencies instead", "2.1.19")
  boms: Seq[(Module, String)] = Nil,
  @since("2.1.19")
  bomDependencies: Seq[BomDependency] = Nil,
  @since("2.1.23")
  overridesMap: Overrides =
    Overrides.empty,
  @since("2.1.25")
  endorseStrictVersions: Boolean = false
) {
  assertValid(versionConstraint.asString, "version")
  def moduleVersionConstraint: (Module, VersionConstraint0) = (module, versionConstraint)

  @deprecated("Prefer moduleVersionConstraint instead", "2.1.25")
  def moduleVersion: (Module, String) = (module, versionConstraint.asString)

  def asBomDependency: BomDependency = {
    val config = variantSelector match {
      case c: VariantSelector.ConfigurationBased => c.configuration
      case _: VariantSelector.AttributesBased    => Configuration.empty
    }
    BomDependency(module, versionConstraint, config)
  }

  @deprecated(
    "Prefer using versionConstraint instead (versionConstraint.asString to get a printable string)",
    "2.1.25"
  )
  def version: String =
    versionConstraint.asString
  @deprecated("Prefer withVersionConstraint instead", "2.1.25")
  def withVersion(newVersion: String): Dependency =
    if (newVersion == version) this
    else withVersionConstraint(VersionConstraint0(newVersion))

  @deprecated("Use variantSelector instead", "2.1.25")
  def configuration: Configuration =
    variantSelector match {
      case c: VariantSelector.ConfigurationBased => c.configuration
      case _: VariantSelector.AttributesBased    =>
        sys.error("Deprecated method doesn't support Gradle Module variant selectors")
    }
  @deprecated("Use withVariantSelector instead", "2.1.25")
  def withConfiguration(newConfiguration: Configuration): Dependency =
    if (variantSelector.asConfiguration.contains(newConfiguration)) this
    else withVariantSelector(VariantSelector.ConfigurationBased(newConfiguration))

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def this(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ) =
    this(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def this(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map
  ) =
    this(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def this(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map,
    boms: Seq[(Module, String)]
  ) =
    this(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def this(
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
  ) =
    this(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms,
      bomDependencies
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def this(
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
  ) =
    this(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms,
      bomDependencies,
      overridesMap
    )

  def this(
    module: Module,
    version: VersionConstraint0,
    configuration: Configuration,
    minimizedExclusions: Set[(Organization, ModuleName)],
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ) = this(
    module,
    version,
    VariantSelector.ConfigurationBased(configuration),
    MinimizedExclusions(minimizedExclusions),
    publication,
    optional,
    transitive
  )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
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
    VersionConstraint0(version),
    VariantSelector.ConfigurationBased(configuration),
    MinimizedExclusions(minimizedExclusions),
    publication,
    optional,
    transitive
  )

  private def deprecatedBoms = DependencyInternals.deprecatedBoms(this)

  def mavenPrefix: String =
    Dependency.mavenPrefix(module, attributes)

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

  def addExclusion(org: Organization, name: ModuleName): Dependency =
    withMinimizedExclusions(
      minimizedExclusions.join(
        MinimizedExclusions(Set((org, name)))
      )
    )

  def addBom(bomDep: BomDependency): Dependency =
    withBomDependencies(bomDependencies :+ bomDep)
  def addBom(module: Module, version: VersionConstraint0): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(module, version, Configuration.empty))
  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def addBom(module: Module, version: String): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(
      module,
      VersionConstraint0(version),
      Configuration.empty
    ))
  def addBom(module: Module, version: VersionConstraint0, config: Configuration): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(module, version, config))
  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def addBom(module: Module, version: String, config: Configuration): Dependency =
    withBomDependencies(bomDependencies :+ BomDependency(
      module,
      VersionConstraint0(version),
      config
    ))
  def addBoms0(boms: Seq[(Module, VersionConstraint0)]): Dependency =
    withBomDependencies(
      this.bomDependencies ++
        boms.map(t => BomDependency(t._1, t._2, Configuration.empty))
    )
  @deprecated("Prefer addBoms0 instead, that accepts a VersionConstraint", "2.1.25")
  def addBoms(boms: Seq[(Module, String)]): Dependency =
    withBomDependencies(
      this.bomDependencies ++
        boms.map(t => BomDependency(t._1, VersionConstraint0(t._2), Configuration.empty))
    )
  def addBomDependencies(bomDependencies: Seq[BomDependency]): Dependency =
    withBomDependencies(this.bomDependencies ++ bomDependencies)

  def addOverride(key: DependencyManagement.Key, values: DependencyManagement.Values): Dependency =
    withOverridesMap(
      Overrides.add(overridesMap, Overrides(Map(key -> values)))
    )
  def addOverride(org: Organization, name: ModuleName, version: VersionConstraint0): Dependency = {
    val key    = DependencyManagement.Key(org, name, Type.jar, Classifier.empty)
    val values = DependencyManagement.Values(
      Configuration.empty,
      version,
      MinimizedExclusions.zero,
      optional = false
    )
    addOverride(key, values)
  }
  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def addOverride(org: Organization, name: ModuleName, version: String): Dependency =
    addOverride(org, name, VersionConstraint0(version))
  def addOverride(
    org: Organization,
    name: ModuleName,
    version: VersionConstraint0,
    exclusions: Set[(Organization, ModuleName)]
  ): Dependency = {
    val key    = DependencyManagement.Key(org, name, Type.jar, Classifier.empty)
    val values = DependencyManagement.Values(
      Configuration.empty,
      version,
      MinimizedExclusions(exclusions),
      optional = false
    )
    addOverride(key, values)
  }
  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def addOverride(
    org: Organization,
    name: ModuleName,
    version: String,
    exclusions: Set[(Organization, ModuleName)]
  ): Dependency =
    addOverride(org, name, VersionConstraint0(version), exclusions)
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

  def isVariantAttributesBased: Boolean =
    variantSelector match {
      case _: VariantSelector.ConfigurationBased => false
      case _: VariantSelector.AttributesBased    => true
    }
  def addVariantAttributes(attributes: (String, VariantSelector.VariantMatcher)*): Dependency = {
    val (attr, force) = variantSelector match {
      case c: VariantSelector.ConfigurationBased =>
        (VariantSelector.AttributesBased(), true)
      case a: VariantSelector.AttributesBased =>
        (a, false)
    }
    if (attributes.isEmpty && !force)
      this
    else
      withVariantSelector(attr.addAttributes(attributes: _*))
  }

  private[core] def copy(
    module: Module = this.module,
    version: VersionConstraint0 = this.versionConstraint,
    variantSelector: VariantSelector = this.variantSelector,
    minimizedExclusions: MinimizedExclusions = this.minimizedExclusions,
    attributes: Attributes = this.attributes,
    optional: Boolean = this.optional,
    transitive: Boolean = this.transitive
  ) = Dependency(
    module,
    version,
    variantSelector,
    minimizedExclusions,
    Publication("", attributes.`type`, Extension.empty, attributes.classifier),
    optional,
    transitive,
    Map.empty[DependencyManagement.Key, DependencyManagement.Values],
    deprecatedBoms,
    bomDependencies,
    overridesMap,
    endorseStrictVersions
  )

  lazy val clearExclusions: Dependency =
    if (minimizedExclusions.isEmpty) this
    else withMinimizedExclusions(MinimizedExclusions.zero)
  lazy val clearOverrides: Dependency =
    if (overridesMap.isEmpty) this
    else withOverridesMap(Overrides.empty)
  lazy val clearVersion: Dependency =
    if (versionConstraint.asString.isEmpty) this
    else withVersionConstraint(VersionConstraint0.empty)
  lazy val depManagementKey: DependencyManagement.Key =
    DependencyManagement.Key(
      module.organization,
      module.name,
      if (publication.`type`.isEmpty) Type.jar else publication.`type`,
      publication.classifier
    )
  lazy val hasProperties =
    module.hasProperties ||
    versionConstraint.asString.contains("$") ||
    publication.attributesHaveProperties ||
    variantSelector.asConfiguration.exists(_.value.contains("$")) ||
    minimizedExclusions.hasProperties

  // Overriding toString to be backwards compatible with Set-based exclusion representation
  override def toString(): String = {
    var fields = Seq(
      module.toString,
      versionConstraint.asString,
      variantSelector.asConfiguration.map(_.toString).getOrElse(variantSelector.repr),
      minimizedExclusions.toSet().toString,
      publication.toString,
      optional.toString,
      transitive.toString
    )
    fields =
      if (overridesMap.isEmpty) fields
      else fields :+ overridesMap.flatten.toMap.toString
    fields =
      if (deprecatedBoms.isEmpty) fields
      else fields :+ deprecatedBoms.toString
    fields =
      if (bomDependencies.isEmpty) fields
      else fields :+ bomDependencies.map { bomDep =>
        Seq(
          bomDep.module,
          bomDep.versionConstraint.asString,
          bomDep.config,
          bomDep.forceOverrideVersions
        ).mkString("BomDependency(", ", ", ")")
      }.toString
    if (endorseStrictVersions)
      fields = fields :+ endorseStrictVersions.toString
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
    versionConstraint: VersionConstraint0,
    variantSelector: VariantSelector,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map,
    boms: Seq[(Module, String)],
    bomDependencies: Seq[BomDependency],
    overridesMap: Overrides,
    endorseStrictVersions: Boolean
  ): Dependency =
    coursier.util.Cache.cacheMethod(instanceCache)(
      new Dependency(
        module,
        versionConstraint,
        variantSelector,
        minimizedExclusions,
        publication,
        optional,
        transitive,
        overrides,
        boms,
        bomDependencies,
        overridesMap,
        endorseStrictVersions
      )
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
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
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms,
      bomDependencies,
      overridesMap,
      endorseStrictVersions = false
    )

  def apply(
    module: Module,
    version: VersionConstraint0,
    variantSelector: VariantSelector,
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
      variantSelector,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty[DependencyManagement.Key, DependencyManagement.Values],
      boms,
      bomDependencies,
      Overrides(overrides),
      endorseStrictVersions = false
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
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
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms,
      bomDependencies
    )

  def apply(
    module: Module,
    version: VersionConstraint0,
    variantSelector: VariantSelector,
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
      variantSelector,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty[DependencyManagement.Key, DependencyManagement.Values],
      boms,
      Nil,
      Overrides(overrides),
      endorseStrictVersions = false
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
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
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides,
      boms
    )

  def apply(
    module: Module,
    version: VersionConstraint0,
    variantSelector: VariantSelector,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean,
    overrides: DependencyManagement.Map
  ): Dependency =
    Dependency(
      module,
      version,
      variantSelector,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty[DependencyManagement.Key, DependencyManagement.Values],
      Nil,
      Nil,
      Overrides(overrides),
      endorseStrictVersions = false
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
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
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive,
      overrides
    )

  def apply(
    module: Module,
    version: VersionConstraint0,
    variantSelector: VariantSelector,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    Dependency(
      module,
      version,
      variantSelector,
      minimizedExclusions,
      publication,
      optional,
      transitive,
      Map.empty[DependencyManagement.Key, DependencyManagement.Values],
      Nil,
      Nil,
      Overrides.empty,
      endorseStrictVersions = false
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    minimizedExclusions: MinimizedExclusions,
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      minimizedExclusions,
      publication,
      optional,
      transitive
    )

  def apply(
    module: Module,
    versionConstraint: VersionConstraint0,
    variantSelector: VariantSelector,
    exclusions: Set[(Organization, ModuleName)],
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    Dependency(
      module,
      versionConstraint,
      variantSelector,
      MinimizedExclusions(exclusions),
      publication,
      optional,
      transitive
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    exclusions: Set[(Organization, ModuleName)],
    publication: Publication,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      exclusions,
      publication,
      optional,
      transitive
    )

  def apply(
    module: Module,
    version: VersionConstraint0
  ): Dependency =
    Dependency(
      module,
      version,
      VariantSelector.emptyConfiguration,
      MinimizedExclusions.zero,
      Publication("", Type.empty, Extension.empty, Classifier.empty),
      optional = false,
      transitive = true
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def apply(
    module: Module,
    version: String
  ): Dependency =
    apply(
      module,
      VersionConstraint0(version)
    )

  def apply(
    module: Module,
    version: VersionConstraint0,
    variantSelector: VariantSelector,
    exclusions: Set[(Organization, ModuleName)],
    attributes: Attributes,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    Dependency(
      module,
      version,
      variantSelector,
      MinimizedExclusions(exclusions),
      Publication("", attributes.`type`, Extension.empty, attributes.classifier),
      optional,
      transitive
    )

  @deprecated("Use the override accepting a VersionConstraint", "2.1.25")
  def apply(
    module: Module,
    version: String,
    configuration: Configuration,
    exclusions: Set[(Organization, ModuleName)],
    attributes: Attributes,
    optional: Boolean,
    transitive: Boolean
  ): Dependency =
    apply(
      module,
      VersionConstraint0(version),
      VariantSelector.ConfigurationBased(configuration),
      exclusions,
      attributes,
      optional,
      transitive
    )

  def mavenPrefix(module: Module, attributes: Attributes): String =
    if (attributes.isEmpty)
      module.orgName
    else
      s"${module.orgName}:${attributes.packagingAndClassifier}"
}
