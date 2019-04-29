package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core._
import coursier.params.ResolutionParams
import coursier.parse.{DependencyParser, RuleParser}

final case class ResolutionOptions(

  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,

  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,

  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String] = Nil,

  @Help("Set property in POM files, if it's not already set")
  @Value("name=value")
    property: List[String] = Nil,

  @Help("Force property in POM files")
  @Value("name=value")
    forceProperty: List[String] = Nil,

  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,

  @Help("Default scala version")
  @Short("e")
    scalaVersion: Option[String] = None,

  @Help("Ensure the scala version used by the scala-library/reflect/compiler JARs is coherent, and adjust the scala version for fully cross-versioned dependencies")
    forceScalaVersion: Option[Boolean] = None,

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,

  @Help("Enforce resolution rules")
  @Short("rule")
    rules: List[String] = Nil

) {

  def scalaVersionOrDefault: String =
    scalaVersion.getOrElse(ResolutionParams().selectedScalaVersion)

  def params: ValidatedNel[String, ResolutionParams] = {

    val maxIterationsV =
      if (maxIterations > 0)
        Validated.validNel(maxIterations)
      else
        Validated.invalidNel(s"Max iteration must be > 0 (got $maxIterations")

    val forceVersionV =
      DependencyParser.moduleVersions(
        forceVersion, scalaVersionOrDefault
      ).either match {
        case Left(e) =>
          Validated.invalidNel(
            s"Cannot parse forced versions:\n" +
              e.map("  " + _).mkString("\n")
          )
        case Right(elems) =>
          Validated.validNel(
            elems
              .groupBy(_._1)
              .mapValues(_.map(_._2).last)
              .iterator
              .toMap
          )
      }

    def propertiesV(input: List[String], type0: String) =
      input
        .traverse { s =>
          s.split("=", 2) match {
            case Array(k, v) =>
              Validated.validNel(k -> v)
            case _ =>
              Validated.invalidNel(s"Malformed $type0 argument: $s")
          }
        }

    val extraPropertiesV = propertiesV(property, "property")

    val forcedPropertiesV = propertiesV(forceProperty, "forced property")
      // TODO Warn if some properties are forced multiple times?
      .map(_.toMap)

    val profiles = profile.toSet

    val rulesV = rules
      .traverse { s =>
        RuleParser.rules(s) match {
          case Left(err) => Validated.invalidNel(s"Malformed rules '$s': $err")
          case Right(l) => Validated.validNel(l)
        }
      }
      .map(_.flatten)

    (maxIterationsV, forceVersionV, extraPropertiesV, forcedPropertiesV, rulesV).mapN {
      (maxIterations, forceVersion, extraProperties, forcedProperties, rules) =>
        ResolutionParams()
          .withKeepOptionalDependencies(keepOptional)
          .withMaxIterations(maxIterations)
          .withForceVersion(forceVersion)
          .withProperties(extraProperties)
          .withForcedProperties(forcedProperties)
          .withProfiles(profiles)
          .withScalaVersion(scalaVersion)
          .withForceScalaVersion(forceScalaVersion)
          .withTypelevel(typelevel)
          .withRules(rules)
    }
  }
}

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help = caseapp.core.help.Help[ResolutionOptions]
}
