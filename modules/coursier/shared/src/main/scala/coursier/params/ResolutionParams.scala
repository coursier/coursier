/**
 * This code USED TO BE generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO EDIT MANUALLY from now on
package coursier.params

import coursier.core.{Module, ModuleName, Organization}
import coursier.params.rule.{Rule, RuleResolution}

final class ResolutionParams private (
  val keepOptionalDependencies: Boolean,
  val maxIterations: Int,
  val forceVersion: Map[coursier.core.Module, String],
  val forcedProperties: Map[String, String],
  val profiles: Set[String],
  val scalaVersion: Option[String],
  val forceScalaVersion: Option[Boolean],
  val typelevel: Boolean,
  val rules: Seq[(Rule, RuleResolution)],
  val properties: Seq[(String, String)]
) extends coursier.params.ResolutionParamsHelpers with Serializable {

  private def this() =
    this(false, 200, Map.empty, Map.empty, Set.empty, None, None, false, Nil, Nil)

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
    Nil
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
        properties == x.properties
    case _ => false
  }
  override def hashCode: Int =
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.params.ResolutionParams".##) + keepOptionalDependencies.##) + maxIterations.##) + forceVersion.##) + forcedProperties.##) + profiles.##) + scalaVersion.##) + forceScalaVersion.##) + typelevel.##) + rules.##) + properties.##)
  override def toString: String =
    s"ResolutionParams($keepOptionalDependencies, $maxIterations, $forceVersion, $forcedProperties, $profiles, $scalaVersion, $forceScalaVersion, $typelevel, $rules, $properties)"

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
    properties: Seq[(String, String)] = properties
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
}
