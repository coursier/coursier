package coursier.params

import coursier.params.rule.{Rule, RuleResolution}

abstract class ResolutionParamsHelpers {
  def forceScalaVersion: Option[Boolean]
  def scalaVersion: Option[String]
  def withRules(rules: Seq[(Rule, RuleResolution)]): coursier.params.ResolutionParams

  def doForceScalaVersion: Boolean =
    forceScalaVersion.getOrElse {
      scalaVersion.nonEmpty
    }
  def selectedScalaVersion: String =
    scalaVersion.getOrElse {
      coursier.internal.Defaults.scalaVersion
    }

  final def addRule(rule: Rule, resolution: RuleResolution): coursier.params.ResolutionParams =
    withRules(Seq(rule -> resolution))
  final def addRule(rule: Rule): coursier.params.ResolutionParams =
    addRule(rule, RuleResolution.TryResolve)
}
