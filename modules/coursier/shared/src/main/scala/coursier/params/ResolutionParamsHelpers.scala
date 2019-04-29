package coursier.params

import coursier.core.Module
import coursier.params.rule.{Rule, RuleResolution}

abstract class ResolutionParamsHelpers {
  def forceScalaVersion: Option[Boolean]
  def scalaVersion: Option[String]
  def withRules(rules: Seq[(Rule, RuleResolution)]): ResolutionParams
  def forceVersion: Map[Module, String]
  def withForceVersion(fv: Map[Module, String]): ResolutionParams
  def profiles: Set[String]
  def withProfiles(profiles: Set[String]): ResolutionParams
  def forcedProperties: Map[String, String]
  def withForcedProperties(props: Map[String, String]): ResolutionParams
  def properties: Seq[(String, String)]
  def withProperties(props: Seq[(String, String)]): ResolutionParams

  def addForceVersion(fv: (Module, String)*): ResolutionParams =
    withForceVersion(forceVersion ++ fv)

  def doForceScalaVersion: Boolean =
    forceScalaVersion.getOrElse {
      scalaVersion.nonEmpty
    }
  def selectedScalaVersion: String =
    scalaVersion.getOrElse {
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
}
