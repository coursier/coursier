package coursier.cli.options

import caseapp._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.core.{Configuration, ResolutionProcess, VariantSelector}
import coursier.params.ResolutionParams
import coursier.parse.{DependencyParser, ModuleParser, ReconciliationParser, RuleParser}
import coursier.version.Version
import coursier.version.VersionConstraint

// format: off
final case class ResolutionOptions(
  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @ExtraName("N")
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,

  @Group(OptionGroup.resolution)
  @HelpMessage("Force module version")
  @ValueDescription("organization:name:forcedVersion")
  @ExtraName("V")
    forceVersion: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Set property in POM files, if it's not already set")
  @ValueDescription("name=value")
    pomProperty: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Force property in POM files")
  @ValueDescription("name=value")
    forcePomProperty: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Enable profile")
  @ValueDescription("profile")
    profile: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @HelpMessage("Default scala version")
  @ExtraName("e")
  @ExtraName("scala")
    scalaVersion: Option[String] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Ensure the scala version used by the scala-library/reflect/compiler JARs is coherent")
    forceScalaVersion: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Adjust the scala version for fully cross-versioned dependencies")
    overrideFullSuffix: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Enforce resolution rules")
  @ExtraName("rule")
    rules: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Choose reconciliation strategy")
  @ValueDescription("organization:name:(basic|relaxed)")
    reconciliation: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
    strict: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
    strictInclude: List[String] = Nil,
  @Group(OptionGroup.resolution)
  @Hidden
    strictExclude: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Default configuration (default(runtime) by default)")
  @ValueDescription("configuration")
  @ExtraName("c")
    defaultConfiguration: String = "default(runtime)",

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Keep provided dependencies")
    keepProvidedDependencies: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @HelpMessage("Set JDK version used during resolution when picking profiles")
    jdkVersion: Option[String] = None,

  @Group(OptionGroup.resolution)
  @Hidden
    forceDepMgmtVersions: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
    enableDependencyOverrides: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @ExtraName("variant")
    variants: List[String] = Nil

) {
  // format: on

  def scalaVersionOrDefault: String =
    scalaVersion.getOrElse(ResolutionParams().selectedScalaVersionConstraint.asString)

  def params: ValidatedNel[String, ResolutionParams] = {

    val maxIterationsV =
      if (maxIterations > 0)
        Validated.validNel(maxIterations)
      else
        Validated.invalidNel(s"Max iteration must be > 0 (got $maxIterations")

    val forceVersionV
      : ValidatedNel[String, Map[coursier.core.Module, coursier.version.VersionConstraint]] =
      DependencyParser.moduleVersions0(
        forceVersion,
        scalaVersionOrDefault
      ).either match {
        case Left(e) =>
          Validated.invalidNel(
            s"Cannot parse forced versions:" + System.lineSeparator() +
              e.map("  " + _).mkString(System.lineSeparator())
          )
        case Right(elems) =>
          Validated.validNel(
            elems
              .groupBy(_._1)
              .view.mapValues(_.map(_._2).last)
              .iterator
              .toMap: Map[coursier.core.Module, coursier.version.VersionConstraint]
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

    val extraPropertiesV = propertiesV(pomProperty, "property")

    val forcedPropertiesV = propertiesV(forcePomProperty, "forced property")
      // TODO Warn if some properties are forced multiple times?
      .map(_.toMap)

    val profiles = profile.toSet

    val extraStrictRule =
      if (strict.getOrElse(strictExclude.nonEmpty || strictInclude.nonEmpty)) {
        val modules = strictInclude.map(_.trim).filter(_.nonEmpty) ++
          strictExclude.map(_.trim).filter(_.nonEmpty).map("!" + _)
        List(s"Strict(${modules.mkString(", ")})")
      }
      else
        Nil

    val rulesV = (extraStrictRule ::: rules)
      .traverse { s =>
        RuleParser.rules(s) match {
          case Left(err) => Validated.invalidNel(s"Malformed rules '$s': $err")
          case Right(l)  => Validated.validNel(l)
        }
      }
      .map(_.flatten)

    val reconciliationV =
      ReconciliationParser.reconciliation0(reconciliation, scalaVersionOrDefault).either match {
        case Left(e)      => Validated.invalidNel(e.mkString(System.lineSeparator()))
        case Right(elems) => Validated.validNel(elems)
      }

    val defaultVariantAttributesOptV = {
      val variantsOrErrors = variants
        .filter(_.trim.nonEmpty)
        .map(_.split("=", 2))
        .map {
          case Array(k, v)  => Right((k, v))
          case Array(thing) => Left(s"Malformed variant value: '$thing' (expected 'name=value')")
        }
      val errors = variantsOrErrors.collect {
        case Left(err) => err
      }
      errors match {
        case h :: t =>
          Validated.Invalid(NonEmptyList(h, t))
        case Nil =>
          val values = variantsOrErrors.collect {
            case Right((k, v)) =>
              VariantSelector.VariantMatcher.fromString(k, v)
          }
          val valuesMap = values.toMap
          if (values.distinct.length == valuesMap.size)
            Validated.validNel {
              if (values.isEmpty) None
              else Some(VariantSelector.AttributesBased(values.toMap))
            }
          else {
            val desc = values
              .distinct
              .groupBy(_._1)
              .filter(_._2.length > 1)
              .map(_._1)
              .toVector
              .sorted
              .mkString(", ")
            Validated.invalidNel(
              s"Found duplicated variants: $desc"
            )
          }
      }
    }

    (
      maxIterationsV,
      forceVersionV,
      extraPropertiesV,
      forcedPropertiesV,
      rulesV,
      reconciliationV,
      defaultVariantAttributesOptV
    ).mapN {
      (
        maxIterations,
        forceVersion: Map[coursier.core.Module, coursier.version.VersionConstraint],
        extraProperties,
        forcedProperties,
        rules,
        reconciliation,
        defaultVariantAttributesOpt
      ) =>
        ResolutionParams()
          .withKeepOptionalDependencies(keepOptional)
          .withMaxIterations(maxIterations)
          .withForceVersion0(forceVersion)
          .withProperties(extraProperties)
          .withForcedProperties(forcedProperties)
          .withProfiles(profiles)
          .withScalaVersionOpt0(
            scalaVersion
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(VersionConstraint(_))
          )
          .withForceScalaVersionOpt(forceScalaVersion)
          .withOverrideFullSuffixOpt(overrideFullSuffix)
          .withTypelevel(typelevel)
          .withRules(rules)
          .withReconciliation0(reconciliation)
          .withDefaultConfiguration(Configuration(defaultConfiguration))
          .withKeepProvidedDependencies(keepProvidedDependencies)
          .withJdkVersionOpt0(jdkVersion.map(_.trim).filter(_.nonEmpty).map(Version(_)))
          .withForceDepMgmtVersions(forceDepMgmtVersions)
          .withEnableDependencyOverrides(enableDependencyOverrides)
          .withDefaultVariantAttributes(defaultVariantAttributesOpt)
    }
  }
}

object ResolutionOptions {
  implicit lazy val parser: Parser[ResolutionOptions] = Parser.derive
  implicit lazy val help: Help[ResolutionOptions]     = Help.derive
}
