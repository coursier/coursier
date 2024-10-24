package coursier.params

import coursier.core.{
  Activation,
  Configuration,
  Module,
  ModuleName,
  Organization,
  Reconciliation,
  Version
}
import coursier.params.rule.{Rule, RuleResolution, Strict}
import coursier.util.ModuleMatchers
import dataclass.data

@data class ResolutionParams(
  keepOptionalDependencies: Boolean = false,
  maxIterations: Int = 200,
  forceVersion: Map[Module, String] = Map.empty,
  forcedProperties: Map[String, String] = Map.empty,
  profiles: Set[String] = Set.empty,
  scalaVersionOpt: Option[String] = None,
  forceScalaVersionOpt: Option[Boolean] = None,
  typelevel: Boolean = false,
  rules: Seq[(Rule, RuleResolution)] = Seq.empty,
  reconciliation: Seq[(ModuleMatchers, Reconciliation)] = Nil,
  properties: Seq[(String, String)] = Nil,
  exclusions: Set[(Organization, ModuleName)] = Set.empty,
  osInfoOpt: Option[Activation.Os] = None,
  jdkVersionOpt: Option[Version] = None,
  useSystemOsInfo: Boolean = true,
  useSystemJdkVersion: Boolean = true,
  defaultConfiguration: Configuration = Configuration.defaultCompile,
  @since("2.0.17")
  overrideFullSuffixOpt: Option[Boolean] = None,
  @since("2.1.9")
  keepProvidedDependencies: Option[Boolean] = None,
  @since("2.1.15")
  forceDepMgmtVersions: Option[Boolean] = None
) {

  def addForceVersion(fv: (Module, String)*): ResolutionParams =
    withForceVersion(forceVersion ++ fv)

  def doForceScalaVersion: Boolean =
    forceScalaVersionOpt.getOrElse {
      scalaVersionOpt.nonEmpty
    }
  def doOverrideFullSuffix: Boolean =
    overrideFullSuffixOpt.getOrElse(false)
  def selectedScalaVersion: String =
    scalaVersionOpt.getOrElse {
      coursier.internal.Defaults.scalaVersion
    }

  def addProfile(profile: String*): ResolutionParams =
    withProfiles(profiles ++ profile)

  final def addRule(rule: Rule, resolution: RuleResolution): coursier.params.ResolutionParams =
    withRules(Seq(rule -> resolution))
  final def addRule(rule: Rule): coursier.params.ResolutionParams =
    addRule(rule, RuleResolution.TryResolve)

  def addProperties(props: (String, String)*): ResolutionParams =
    withProperties(properties ++ props)
  def addForcedProperties(props: (String, String)*): ResolutionParams =
    withForcedProperties(forcedProperties ++ props)

  def withScalaVersion(scalaVersion: String): ResolutionParams =
    withScalaVersionOpt(Option(scalaVersion))
  def withForceScalaVersion(forceScalaVersion: Boolean): ResolutionParams =
    withForceScalaVersionOpt(Option(forceScalaVersion))
  def withOsInfo(osInfo: Activation.Os): ResolutionParams =
    withOsInfoOpt(Some(osInfo))
  def withJdkVersion(version: String): ResolutionParams =
    withJdkVersionOpt(Some(Version(version)))
  def withJdkVersion(version: Version): ResolutionParams =
    withJdkVersionOpt(Some(version))

  def withKeepProvidedDependencies(keepProvidedDependencies: Boolean): ResolutionParams =
    withKeepProvidedDependencies(Some(keepProvidedDependencies))

  def addReconciliation(reconciliation: (ModuleMatchers, Reconciliation)*): ResolutionParams =
    withReconciliation(this.reconciliation ++ reconciliation)
  def addExclusions(exclusions: (Organization, ModuleName)*): ResolutionParams =
    withExclusions(this.exclusions ++ exclusions)

  def actualReconciliation: Seq[(ModuleMatchers, Reconciliation)] =
    reconciliation.map {
      case (m, Reconciliation.Strict | Reconciliation.SemVer) => (m, Reconciliation.Default)
      case other                                              => other
    }

  lazy val actualRules: Seq[(Rule, RuleResolution)] = {

    val fromReconciliation = reconciliation.collect {
      case (m, Reconciliation.Strict) =>
        (Strict(m.include, m.exclude, includeByDefault = m.includeByDefault), RuleResolution.Fail)
      case (m, Reconciliation.SemVer) =>
        (
          Strict(m.include, m.exclude, includeByDefault = m.includeByDefault).withSemVer(true),
          RuleResolution.Fail
        )
    }

    rules ++ fromReconciliation
  }
}
