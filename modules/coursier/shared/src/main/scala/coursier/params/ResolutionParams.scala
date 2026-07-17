package coursier.params

import coursier.core.{
  Activation,
  Configuration,
  Module,
  ModuleName,
  Organization,
  Reconciliation,
  VariantSelector
}
import coursier.params.rule.{Rule, RuleResolution, Strict}
import coursier.util.ModuleMatchers
import coursier.version.{ConstraintReconciliation, Version, VersionConstraint}
import scala.annotation.unroll

final case class ResolutionParams(
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
  @unroll
  overrideFullSuffixOpt: Option[Boolean] = None,
  @unroll
  keepProvidedDependencies: Option[Boolean] = None,
  @unroll
  forceDepMgmtVersions: Option[Boolean] = None,
  enableDependencyOverrides: Option[Boolean] = None,
  @unroll
  defaultVariantAttributes: Option[VariantSelector.AttributesBased] = None,
  renderModuleVersion: Option[(Module, String) => String] = None,
  @unroll
  scalaOrganizationOverride: Option[Organization] = None
) {

  @deprecated("Use forceVersion0 instead", "2.1.25")
  def forceVersion: Map[Module, String] =
    forceVersion0.map {
      case (k, v) =>
        (k, v.asString)
    }
  @deprecated("Use withForceVersion0 instead", "2.1.25")
  def withForceVersion(newForceVersion: Map[Module, String]): ResolutionParams =
    copy(forceVersion0 = 
      newForceVersion.map {
        case (k, v) =>
          (k, VersionConstraint(v))
      }
    )
  @deprecated("Use scalaVersionOpt0 instead", "2.1.25")
  def scalaVersionOpt: Option[String] =
    scalaVersionOpt0.map(_.asString)
  @deprecated("Use withScalaVersionOpt0 instead", "2.1.25")
  def withScalaVersionOpt(newScalaVersionOpt: Option[String]): ResolutionParams =
    copy(scalaVersionOpt0 = newScalaVersionOpt.map(VersionConstraint(_)))

  @deprecated("Use reconciliation0 instead", "2.1.25")
  def reconciliation: Seq[(ModuleMatchers, Reconciliation)] =
    reconciliation0.map {
      case (m, ConstraintReconciliation.Default) =>
        (m, Reconciliation.Default)
      case (m, ConstraintReconciliation.Relaxed) =>
        (m, Reconciliation.Relaxed)
      case (m, ConstraintReconciliation.Strict) =>
        (m, Reconciliation.Strict)
      case (m, ConstraintReconciliation.SemVer) =>
        (m, Reconciliation.SemVer)
    }
  @deprecated("Use withReconciliation0 instead", "2.1.25")
  def withReconciliation(newReconciliation: Seq[(ModuleMatchers, Reconciliation)])
    : ResolutionParams =
    copy(reconciliation0 = 
      newReconciliation.map {
        case (m, Reconciliation.Default) =>
          (m, ConstraintReconciliation.Default)
        case (m, Reconciliation.Relaxed) =>
          (m, ConstraintReconciliation.Relaxed)
        case (m, Reconciliation.Strict) =>
          (m, ConstraintReconciliation.Strict)
        case (m, Reconciliation.SemVer) =>
          (m, ConstraintReconciliation.SemVer)
      }
    )

  @deprecated("Use jdkVersionOpt0 instead", "2.1.25")
  def jdkVersionOpt: Option[String] =
    jdkVersionOpt0.map(_.repr)
  @deprecated("Use withJdkVersionOpt0 instead", "2.1.25")
  def withJdkVersionOpt(newJdkVersionOpt: Option[String]): ResolutionParams =
    copy(jdkVersionOpt0 = newJdkVersionOpt.map(Version(_)))

  def addForceVersion0(fv: (Module, VersionConstraint)*): ResolutionParams =
    copy(forceVersion0 = forceVersion0 ++ fv)

  @deprecated("Use addForceVersion0 instead", "2.1.25")
  def addForceVersion(fv: (Module, String)*): ResolutionParams =
    addForceVersion0(
      fv.map {
        case (m, v) =>
          (m, VersionConstraint(v))
      }: _*
    )

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

  @deprecated("Use selectedScalaVersionConstraint instead", "2.1.25")
  def selectedScalaVersion: String =
    selectedScalaVersionConstraint.asString

  def addProfile(profile: String*): ResolutionParams =
    copy(profiles = profiles ++ profile)

  final def addRule(rule: Rule, resolution: RuleResolution): coursier.params.ResolutionParams =
    copy(rules = Seq(rule -> resolution))
  final def addRule(rule: Rule): coursier.params.ResolutionParams =
    addRule(rule, RuleResolution.TryResolve)

  def addProperties(props: (String, String)*): ResolutionParams =
    copy(properties = properties ++ props)
  def addForcedProperties(props: (String, String)*): ResolutionParams =
    copy(forcedProperties = forcedProperties ++ props)

  def withScalaVersion(scalaVersion: String): ResolutionParams =
    copy(scalaVersionOpt0 = Option(scalaVersion).map(VersionConstraint(_)))
  def withForceScalaVersion(forceScalaVersion: Boolean): ResolutionParams =
    copy(forceScalaVersionOpt = Option(forceScalaVersion))
  def withOsInfo(osInfo: Activation.Os): ResolutionParams =
    copy(osInfoOpt = Some(osInfo))
  @deprecated("Use the override accepting a coursier.version.Version instead", "2.1.25")
  def withJdkVersion(version: String): ResolutionParams =
    copy(jdkVersionOpt0 = Some(Version(version)))
  @deprecated("Use the override accepting a coursier.version.Version instead", "2.1.25")
  def withJdkVersion(version: coursier.core.Version): ResolutionParams =
    copy(jdkVersionOpt0 = Some(Version(version.repr)))
  def withJdkVersion(version: Version): ResolutionParams =
    copy(jdkVersionOpt0 = Some(version))

  def withKeepProvidedDependencies(keepProvidedDependencies: Boolean): ResolutionParams =
    copy(keepProvidedDependencies = Some(keepProvidedDependencies))

  def withDefaultVariantAttributes(attributes: VariantSelector.AttributesBased): ResolutionParams =
    copy(defaultVariantAttributes = Some(attributes))

  def addReconciliation(reconciliation: (ModuleMatchers, ConstraintReconciliation)*)
    : ResolutionParams =
    copy(reconciliation0 = this.reconciliation0 ++ reconciliation)
  def addExclusions(exclusions: (Organization, ModuleName)*): ResolutionParams =
    copy(exclusions = this.exclusions ++ exclusions)

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
          Strict(m.include, m.exclude, includeByDefault = m.includeByDefault).copy(semVer = true),
          RuleResolution.Fail
        )
    }

    rules ++ fromReconciliation
  }

  /** Add variant attributes to be taken into account when picking Gradle Module variants
    */
  def addVariantAttributes(attributes: (String, VariantSelector.VariantMatcher)*)
    : ResolutionParams =
    withDefaultVariantAttributes(
      finalDefaultVariantAttributes.addAttributes(attributes: _*)
    )

  def finalDefaultVariantAttributes: VariantSelector.AttributesBased =
    defaultVariantAttributes.getOrElse(
      VariantSelector.ConfigurationBased(defaultConfiguration)
        .equivalentAttributesSelector
        .getOrElse(VariantSelector.AttributesBased.empty)
    )
}

object ResolutionParams {
  def defaultRenderModuleVersion(module: Module, version: String): String =
    s"${module.repr}:$version"
}
