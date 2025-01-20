package coursier.params

import coursier.core.{Activation, Configuration, Module, ModuleName, Organization, Reconciliation}
import coursier.params.rule.{Rule, RuleResolution, Strict}
import coursier.util.ModuleMatchers
import coursier.version.{ConstraintReconciliation, Version, VersionConstraint}
import dataclass.data

@data class ResolutionParams(
  keepOptionalDependencies: Boolean = false,
  maxIterations: Int = 200,
  forceVersion0: Map[Module, VersionConstraint] = Map.empty,
  forcedProperties: Map[String, String] = Map.empty,
  profiles: Set[String] = Set.empty,
  scalaVersionOpt0: Option[VersionConstraint] = None,
  forceScalaVersionOpt: Option[Boolean] = None,
  typelevel: Boolean = false,
  rules: Seq[(Rule, RuleResolution)] = Seq.empty,
  reconciliation0: Seq[(ModuleMatchers, ConstraintReconciliation)] = Nil,
  properties: Seq[(String, String)] = Nil,
  exclusions: Set[(Organization, ModuleName)] = Set.empty,
  osInfoOpt: Option[Activation.Os] = None,
  jdkVersionOpt0: Option[Version] = None,
  useSystemOsInfo: Boolean = true,
  useSystemJdkVersion: Boolean = true,
  defaultConfiguration: Configuration = Configuration.defaultRuntime,
  @since("2.0.17")
  overrideFullSuffixOpt: Option[Boolean] = None,
  @since("2.1.9")
  keepProvidedDependencies: Option[Boolean] = None,
  @since("2.1.17")
  forceDepMgmtVersions: Option[Boolean] = None,
  enableDependencyOverrides: Option[Boolean] = None
) {

  def addForceVersion0(fv: (Module, VersionConstraint)*): ResolutionParams =
    withForceVersion0(forceVersion0 ++ fv)

  def doForceScalaVersion: Boolean =
    forceScalaVersionOpt.getOrElse {
      scalaVersionOpt0.nonEmpty
    }
  def doOverrideFullSuffix: Boolean =
    overrideFullSuffixOpt.getOrElse(false)
  def selectedScalaVersionConstraint: VersionConstraint =
    scalaVersionOpt0.getOrElse {
      coursier.internal.Defaults.scalaVersionConstraint
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
    withScalaVersionOpt0(Option(scalaVersion).map(VersionConstraint(_)))
  def withForceScalaVersion(forceScalaVersion: Boolean): ResolutionParams =
    withForceScalaVersionOpt(Option(forceScalaVersion))
  def withOsInfo(osInfo: Activation.Os): ResolutionParams =
    withOsInfoOpt(Some(osInfo))
  def withJdkVersion(version: Version): ResolutionParams =
    withJdkVersionOpt0(Some(version))

  def withKeepProvidedDependencies(keepProvidedDependencies: Boolean): ResolutionParams =
    withKeepProvidedDependencies(Some(keepProvidedDependencies))

  def addReconciliation(reconciliation: (ModuleMatchers, ConstraintReconciliation)*)
    : ResolutionParams =
    withReconciliation0(this.reconciliation0 ++ reconciliation)
  def addExclusions(exclusions: (Organization, ModuleName)*): ResolutionParams =
    withExclusions(this.exclusions ++ exclusions)

  def actualReconciliation: Seq[(ModuleMatchers, ConstraintReconciliation)] =
    reconciliation0.map {
      case (m, ConstraintReconciliation.Strict | ConstraintReconciliation.SemVer) =>
        (m, ConstraintReconciliation.Default)
      case other => other
    }

  lazy val actualRules: Seq[(Rule, RuleResolution)] = {

    val fromReconciliation = reconciliation0.collect {
      case (m, ConstraintReconciliation.Strict) =>
        (Strict(m.include, m.exclude, includeByDefault = m.includeByDefault), RuleResolution.Fail)
      case (m, ConstraintReconciliation.SemVer) =>
        (
          Strict(m.include, m.exclude, includeByDefault = m.includeByDefault).withSemVer(true),
          RuleResolution.Fail
        )
    }

    rules ++ fromReconciliation
  }
}
