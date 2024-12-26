package coursier.cli.options

import caseapp._
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core._
import coursier.params.ResolutionParams
import coursier.parse.{DependencyParser, ModuleParser, ReconciliationParser, RuleParser}

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
    enableDependencyOverrides: Option[Boolean] = None

) {
  // format: on

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
      ReconciliationParser.reconciliation(reconciliation, scalaVersionOrDefault).either match {
        case Left(e)      => Validated.invalidNel(e.mkString(System.lineSeparator()))
        case Right(elems) => Validated.validNel(elems)
      }

    (
      maxIterationsV,
      forceVersionV,
      extraPropertiesV,
      forcedPropertiesV,
      rulesV,
      reconciliationV
    ).mapN {
      (maxIterations, forceVersion, extraProperties, forcedProperties, rules, reconciliation) =>
        ResolutionParams()
          .withKeepOptionalDependencies(keepOptional)
          .withMaxIterations(maxIterations)
          .withForceVersion(forceVersion)
          .withProperties(extraProperties)
          .withForcedProperties(forcedProperties)
          .withProfiles(profiles)
          .withScalaVersionOpt(scalaVersion.map(_.trim).filter(_.nonEmpty))
          .withForceScalaVersionOpt(forceScalaVersion)
          .withOverrideFullSuffixOpt(overrideFullSuffix)
          .withTypelevel(typelevel)
          .withRules(rules)
          .withReconciliation(reconciliation)
          .withDefaultConfiguration(Configuration(defaultConfiguration))
          .withKeepProvidedDependencies(keepProvidedDependencies)
          .withJdkVersionOpt(jdkVersion.map(_.trim).filter(_.nonEmpty).map(Version(_)))
          .withForceDepMgmtVersions(forceDepMgmtVersions)
          .withEnableDependencyOverrides(enableDependencyOverrides)
    }
  }
}

object ResolutionOptions {
  lazy val parser: Parser[ResolutionOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[ResolutionOptions, parser.D] = parser
  implicit lazy val help: Help[ResolutionOptions]                      = Help.derive
}
