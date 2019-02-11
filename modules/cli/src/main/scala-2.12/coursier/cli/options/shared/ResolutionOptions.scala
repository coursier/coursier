package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core._
import coursier.params.ResolutionParams
import coursier.util.Parse

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

  @Help("Force property in POM files")
  @Value("name=value")
    forceProperty: List[String] = Nil,

  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,

  @Help("Default scala version")
  @Short("e")
    scalaVersion: String = scala.util.Properties.versionNumberString,

  @Help("Ensure the scala version used by the scala-library/reflect/compiler JARs is coherent, and adjust the scala version for fully cross-versioned dependencies")
    forceScalaVersion: Boolean = false,

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false

) {

  def params: ValidatedNel[String, ResolutionParams] = {

    val maxIterationsV =
      if (maxIterations > 0)
        Validated.validNel(maxIterations)
      else
        Validated.invalidNel(s"Max iteration must be > 0 (got $maxIterations")

    val forceVersionV = {

      val (forceVersionErrors, forceVersions0) =
        Parse.moduleVersions(forceVersion, scalaVersion)

      if (forceVersionErrors.nonEmpty)
        Validated.invalidNel(
          s"Cannot parse forced versions:\n" + forceVersionErrors.map("  " + _).mkString("\n")
        )
      else
        // TODO Warn if some versions are forced multiple times?
        Validated.validNel(
          forceVersions0
            .groupBy(_._1)
            .mapValues(_.map(_._2).last)
        )
    }

    val forcedPropertiesV = forceProperty
      .traverse { s =>
        s.split("=", 2) match {
          case Array(k, v) =>
            Validated.validNel(k -> v)
          case _ =>
            Validated.invalidNel(s"Malformed forced property argument: $s")
        }
      }
      // TODO Warn if some properties are forced multiple times?
      .map(_.toMap)

    val profiles = profile.toSet

    (maxIterationsV, forceVersionV, forcedPropertiesV).mapN {
      (maxIterations, forceVersion, forcedProperties) =>
        ResolutionParams(
          keepOptional,
          maxIterations,
          forceVersion,
          forcedProperties,
          profiles,
          scalaVersion,
          forceScalaVersion,
          typelevel
        )
    }
  }
}

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help = caseapp.core.help.Help[ResolutionOptions]
}
