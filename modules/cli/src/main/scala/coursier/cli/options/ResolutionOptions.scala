package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core._
import coursier.params.ResolutionParams
import coursier.parse.{DependencyParser, ModuleParser, ReconciliationParser, RuleParser}

// format: off
final case class ResolutionOptions(
  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,

  @Group(OptionGroup.resolution)
  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Set property in POM files, if it's not already set")
  @Value("name=value")
    pomProperty: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Force property in POM files")
  @Value("name=value")
    forcePomProperty: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Enable profile")
  @Value("profile")
    profile: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Help("Default scala version")
  @Short("e")
  @Short("scala")
    scalaVersion: Option[String] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Ensure the scala version used by the scala-library/reflect/compiler JARs is coherent")
    forceScalaVersion: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Adjust the scala version for fully cross-versioned dependencies")
    overrideFullSuffix: Option[Boolean] = None,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Enforce resolution rules")
  @Short("rule")
    rules: List[String] = Nil,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Choose reconciliation strategy")
  @Value("organization:name:(basic|relaxed)")
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
  @Help("Default configuration (default(compile) by default)")
  @Value("configuration")
  @Short("c")
    defaultConfiguration: String = "default(compile)",

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Keep provided dependencies")
    keepProvidedDependencies: Boolean = false,

  @Group(OptionGroup.resolution)
  @Hidden
  @Help("Set JDK version used during resolution when picking profiles")
    jdkVersion: Option[String] = None,

  @Group(OptionGroup.resolution)
  @Hidden
    forceDepMgmtVersions: Option[Boolean] = None

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
    }
  }
}

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help   = caseapp.core.help.Help[ResolutionOptions]
}
