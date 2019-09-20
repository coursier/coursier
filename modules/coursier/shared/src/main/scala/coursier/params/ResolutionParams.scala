/**
 * This code USED TO BE generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO EDIT MANUALLY from now on
package coursier.params

import coursier.core.{Activation, Configuration, Module, ModuleName, Organization, Reconciliation, Version}
import coursier.params.rule.{Rule, RuleResolution, Strict}
import coursier.util.ModuleMatchers

final class ResolutionParams private (
  val keepOptionalDependencies: Boolean,
  val maxIterations: Int,
  val forceVersion: Map[Module, String],
  val forcedProperties: Map[String, String],
  val profiles: Set[String],
  val scalaVersion: Option[String],
  val forceScalaVersion: Option[Boolean],
  val typelevel: Boolean,
  val rules: Seq[(Rule, RuleResolution)],
  val reconciliation: Seq[(ModuleMatchers, Reconciliation)],
  val properties: Seq[(String, String)],
  val exclusions: Set[(Organization, ModuleName)],
  val osInfoOpt: Option[Activation.Os],
  val jdkVersionOpt: Option[Version],
  val useSystemOsInfo: Boolean,
  val useSystemJdkVersion: Boolean,
  val defaultConfiguration: Configuration
) extends coursier.params.ResolutionParamsHelpers with Serializable {

  private def this(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: Option[String],
    forceScalaVersion: Option[Boolean],
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    properties: Seq[(String, String)],
    exclusions: Set[(Organization, ModuleName)]
  ) = this(
    keepOptionalDependencies,
    maxIterations,
    forceVersion,
    forcedProperties,
    profiles,
    scalaVersion,
    forceScalaVersion,
    typelevel,
    rules,
    Nil,
    properties,
    exclusions,
    None,
    None,
    // set these to false by default?
    useSystemOsInfo = true,
    useSystemJdkVersion = true,
    Configuration.defaultCompile
  )

  private def this() =
    this(false, 200, Map.empty, Map.empty, Set.empty, None, None, false, Nil, Nil, Set.empty)

  private def this(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: Option[String],
    forceScalaVersion: Option[Boolean],
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)]
  ) = this(
    keepOptionalDependencies,
    maxIterations,
    forceVersion,
    forcedProperties,
    profiles,
    scalaVersion,
    forceScalaVersion,
    typelevel,
    rules,
    Nil,
    Set.empty
  )

  private def this(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: Option[String],
    forceScalaVersion: Option[Boolean],
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    properties: Seq[(String, String)]
  ) = this(
    keepOptionalDependencies,
    maxIterations,
    forceVersion,
    forcedProperties,
    profiles,
    scalaVersion,
    forceScalaVersion,
    typelevel,
    rules,
    properties,
    Set.empty
  )

  override def equals(o: Any): Boolean = o match {
    case x: ResolutionParams =>
      keepOptionalDependencies == x.keepOptionalDependencies &&
        maxIterations == x.maxIterations &&
        forceVersion == x.forceVersion &&
        forcedProperties == x.forcedProperties &&
        profiles == x.profiles &&
        scalaVersion == x.scalaVersion &&
        forceScalaVersion == x.forceScalaVersion &&
        typelevel == x.typelevel &&
        rules == x.rules &&
        reconciliation == x.reconciliation &&
        properties == x.properties &&
        exclusions == x.exclusions &&
        osInfoOpt == x.osInfoOpt &&
        jdkVersionOpt == x.jdkVersionOpt &&
        useSystemOsInfo == x.useSystemOsInfo &&
        useSystemJdkVersion == x.useSystemJdkVersion &&
        defaultConfiguration == x.defaultConfiguration
    case _ => false
  }
  override def hashCode: Int = {
    var code = 37 * (17 + "coursier.params.ResolutionParams".##)
    code = 37 * (code + keepOptionalDependencies.##)
    code = 37 * (code + maxIterations.##)
    code = 37 * (code + forceVersion.##)
    code = 37 * (code + forcedProperties.##)
    code = 37 * (code + profiles.##)
    code = 37 * (code + scalaVersion.##)
    code = 37 * (code + forceScalaVersion.##)
    code = 37 * (code + typelevel.##)
    code = 37 * (code + rules.##)
    code = 37 * (code + reconciliation.##)
    code = 37 * (code + properties.##)
    code = 37 * (code + exclusions.##)
    code = 37 * (code + osInfoOpt.##)
    code = 37 * (code + jdkVersionOpt.##)
    code = 37 * (code + useSystemOsInfo.##)
    code = 37 * (code + useSystemJdkVersion.##)
    code = 37 * (code + defaultConfiguration.##)
    code
  }
  override def toString: String = {
    val b = List.newBuilder[Any]
    b ++= Seq(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      scalaVersion,
      forceScalaVersion,
      typelevel,
      rules,
      reconciliation,
      properties,
      exclusions,
      osInfoOpt,
      jdkVersionOpt,
      useSystemOsInfo,
      useSystemJdkVersion
    )
    if (defaultConfiguration != Configuration.compile)
      b += defaultConfiguration
    b.result().mkString("ResolutionParams(", ", ", ")")
  }

  private[this] def copy(
    keepOptionalDependencies: Boolean = keepOptionalDependencies,
    maxIterations: Int = maxIterations,
    forceVersion: Map[Module, String] = forceVersion,
    forcedProperties: Map[String, String] = forcedProperties,
    profiles: Set[String] = profiles,
    scalaVersion: Option[String] = scalaVersion,
    forceScalaVersion: Option[Boolean] = forceScalaVersion,
    typelevel: Boolean = typelevel,
    rules: Seq[(Rule, RuleResolution)] = rules,
    reconciliation: Seq[(ModuleMatchers, Reconciliation)] = reconciliation,
    properties: Seq[(String, String)] = properties,
    exclusions: Set[(Organization, ModuleName)] = exclusions,
    osInfoOpt: Option[Activation.Os] = osInfoOpt,
    jdkVersionOpt: Option[Version] = jdkVersionOpt,
    useSystemOsInfo: Boolean = useSystemOsInfo,
    useSystemJdkVersion: Boolean = useSystemJdkVersion,
    defaultConfiguration: Configuration = defaultConfiguration
  ): ResolutionParams =
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      scalaVersion,
      forceScalaVersion,
      typelevel,
      rules,
      reconciliation,
      properties,
      exclusions,
      osInfoOpt,
      jdkVersionOpt,
      useSystemOsInfo,
      useSystemJdkVersion,
      defaultConfiguration
    )

  def withKeepOptionalDependencies(keepOptionalDependencies: Boolean): ResolutionParams =
    copy(keepOptionalDependencies = keepOptionalDependencies)
  def withMaxIterations(maxIterations: Int): ResolutionParams =
    copy(maxIterations = maxIterations)
  def withForceVersion(forceVersion: Map[Module, String]): ResolutionParams =
    copy(forceVersion = forceVersion)
  def withForcedProperties(forcedProperties: Map[String, String]): ResolutionParams =
    copy(forcedProperties = forcedProperties)
  def withProperties(properties: Seq[(String, String)]): ResolutionParams =
    copy(properties = properties)
  def withProfiles(profiles: Set[String]): ResolutionParams =
    copy(profiles = profiles)
  def withScalaVersion(scalaVersion: Option[String]): ResolutionParams =
    copy(scalaVersion = scalaVersion)
  def withScalaVersion(scalaVersion: String): ResolutionParams =
    copy(scalaVersion = Option(scalaVersion))
  def withForceScalaVersion(forceScalaVersion: Option[Boolean]): ResolutionParams =
    copy(forceScalaVersion = forceScalaVersion)
  def withForceScalaVersion(forceScalaVersion: Boolean): ResolutionParams =
    copy(forceScalaVersion = Option(forceScalaVersion))
  def withTypelevel(typelevel: Boolean): ResolutionParams =
    copy(typelevel = typelevel)
  def withRules(rules: Seq[(Rule, RuleResolution)]): ResolutionParams =
    copy(rules = rules)
  def withReconciliation(reconciliation: Seq[(ModuleMatchers, Reconciliation)]): ResolutionParams =
    copy(reconciliation = reconciliation)
  def withExclusions(exclusions: Set[(Organization, ModuleName)]): ResolutionParams =
    copy(exclusions = exclusions)
  def withOsInfo(osInfo: Activation.Os): ResolutionParams =
    copy(osInfoOpt = Some(osInfo))
  def withOsInfo(osInfoOpt: Option[Activation.Os]): ResolutionParams =
    copy(osInfoOpt = osInfoOpt)
  def withJdkVersion(version: String): ResolutionParams =
    copy(jdkVersionOpt = Some(Version(version)))
  def withJdkVersion(version: Version): ResolutionParams =
    copy(jdkVersionOpt = Some(version))
  def withJdkVersion(versionOpt: Option[Version]): ResolutionParams =
    copy(jdkVersionOpt = versionOpt)
  def withUseSystemOsInfo(useSystemOsInfo: Boolean): ResolutionParams =
    copy(useSystemOsInfo = useSystemOsInfo)
  def withUseSystemJdkVersion(useSystemJdkVersion: Boolean): ResolutionParams =
    copy(useSystemJdkVersion = useSystemJdkVersion)
  def withDefaultConfiguration(defaultConfiguration: Configuration): ResolutionParams =
    copy(defaultConfiguration = defaultConfiguration)

  def addReconciliation(reconciliation: (ModuleMatchers, Reconciliation)*): ResolutionParams =
    copy(reconciliation = this.reconciliation ++ reconciliation)
  def addExclusions(exclusions: (Organization, ModuleName)*): ResolutionParams =
    copy(exclusions = this.exclusions ++ exclusions)

  def actualReconciliation: Seq[(ModuleMatchers, Reconciliation)] =
    reconciliation.map {
      case (m, Reconciliation.Strict | Reconciliation.SemVer) => (m, Reconciliation.Default)
      case other => other
    }

  lazy val actualRules: Seq[(Rule, RuleResolution)] = {

    val fromReconciliation = reconciliation.collect {
      case (m, Reconciliation.Strict) =>
        (Strict(m.include, m.exclude, includeByDefault = m.includeByDefault), RuleResolution.Fail)
      case (m, Reconciliation.SemVer) =>
        (Strict(m.include, m.exclude, includeByDefault = m.includeByDefault, semVer = true), RuleResolution.Fail)
    }

    rules ++ fromReconciliation
  }
}
object ResolutionParams {

  def apply(): ResolutionParams =
    new ResolutionParams()

  def apply(
    keepOptionalDependencies: Boolean, 
    maxIterations: Int, 
    forceVersion: Map[Module, String], 
    forcedProperties: Map[String, String], 
    profiles: Set[String], 
    scalaVersion: Option[String], 
    forceScalaVersion: Option[Boolean], 
    typelevel: Boolean, 
    rules: Seq[(Rule, RuleResolution)]
  ): ResolutionParams = 
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      scalaVersion,
      forceScalaVersion,
      typelevel,
      rules
    )

  def apply(
    keepOptionalDependencies: Boolean, 
    maxIterations: Int, 
    forceVersion: Map[Module, String], 
    forcedProperties: Map[String, String], 
    profiles: Set[String], 
    scalaVersion: String, 
    forceScalaVersion: Boolean, 
    typelevel: Boolean, 
    rules: Seq[(Rule, RuleResolution)]
  ): ResolutionParams = 
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      Option(scalaVersion),
      Option(forceScalaVersion),
      typelevel,
      rules
    )

  def apply(
    keepOptionalDependencies: Boolean, 
    maxIterations: Int, 
    forceVersion: Map[Module, String], 
    forcedProperties: Map[String, String], 
    profiles: Set[String], 
    scalaVersion: Option[String], 
    forceScalaVersion: Option[Boolean], 
    typelevel: Boolean, 
    rules: Seq[(Rule, RuleResolution)], 
    properties: Seq[(String, String)]
  ): ResolutionParams = 
    new ResolutionParams(
      keepOptionalDependencies, 
      maxIterations, 
      forceVersion, 
      forcedProperties, 
      profiles,
      scalaVersion, 
      forceScalaVersion, 
      typelevel, 
      rules, 
      properties
    )
  
  def apply(
    keepOptionalDependencies: Boolean, 
    maxIterations: Int, 
    forceVersion: Map[Module, String], 
    forcedProperties: Map[String, String], 
    profiles: Set[String], 
    scalaVersion: String, 
    forceScalaVersion: Boolean, 
    typelevel: Boolean, 
    rules: Seq[(Rule, RuleResolution)], 
    properties: Seq[(String, String)]
  ): ResolutionParams = 
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      Option(scalaVersion),
      Option(forceScalaVersion),
      typelevel,
      rules,
      properties
    )

  def apply(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: Option[String],
    forceScalaVersion: Option[Boolean],
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    properties: Seq[(String, String)],
    exclusions: Set[(Organization, ModuleName)]
  ): ResolutionParams =
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      scalaVersion,
      forceScalaVersion,
      typelevel,
      rules,
      properties,
      exclusions
    )

  def apply(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: String,
    forceScalaVersion: Boolean,
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    properties: Seq[(String, String)],
    exclusions: Set[(Organization, ModuleName)]
  ): ResolutionParams =
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      Option(scalaVersion),
      Option(forceScalaVersion),
      typelevel,
      rules,
      properties,
      exclusions
    )

  def apply(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: Option[String],
    forceScalaVersion: Option[Boolean],
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    reconciliation: Seq[(ModuleMatchers, Reconciliation)],
    properties: Seq[(String, String)],
    exclusions: Set[(Organization, ModuleName)],
    osInfoOpt: Option[Activation.Os],
    jdkVersionOpt: Option[Version],
    useSystemOsInfo: Boolean,
    useSystemJdkVersion: Boolean
  ): ResolutionParams =
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      scalaVersion,
      forceScalaVersion,
      typelevel,
      rules,
      reconciliation,
      properties,
      exclusions,
      osInfoOpt,
      jdkVersionOpt,
      useSystemOsInfo,
      useSystemJdkVersion,
      Configuration.defaultCompile
    )

  def apply(
    keepOptionalDependencies: Boolean,
    maxIterations: Int,
    forceVersion: Map[Module, String],
    forcedProperties: Map[String, String],
    profiles: Set[String],
    scalaVersion: String,
    forceScalaVersion: Boolean,
    typelevel: Boolean,
    rules: Seq[(Rule, RuleResolution)],
    reconciliation: Seq[(ModuleMatchers, Reconciliation)],
    properties: Seq[(String, String)],
    exclusions: Set[(Organization, ModuleName)],
    osInfoOpt: Option[Activation.Os],
    jdkVersionOpt: Option[Version],
    useSystemOsInfo: Boolean,
    useSystemJdkVersion: Boolean
  ): ResolutionParams =
    new ResolutionParams(
      keepOptionalDependencies,
      maxIterations,
      forceVersion,
      forcedProperties,
      profiles,
      Option(scalaVersion),
      Option(forceScalaVersion),
      typelevel,
      rules,
      reconciliation,
      properties,
      exclusions,
      osInfoOpt,
      jdkVersionOpt,
      useSystemOsInfo,
      useSystemJdkVersion,
      Configuration.defaultCompile
    )
}
