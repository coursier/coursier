package coursier.cli.params.shared

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.shared.ResolutionOptions
import coursier.core.Module
import coursier.util.Parse

final case class ResolutionParams(
  keepOptionalDependencies: Boolean,
  maxIterations: Int,
  forceVersion: Map[Module, String],
  forcedProperties: Map[String, String],
  profiles: Set[String],
  typelevel: Boolean
)

object ResolutionParams {
  def apply(options: ResolutionOptions, scalaVersion: String): ValidatedNel[String, ResolutionParams] = {

    val maxIterationsV =
      if (options.maxIterations > 0)
        Validated.validNel(options.maxIterations)
      else
        Validated.invalidNel(s"Max iteration must be > 0 (got ${options.maxIterations}")

    val forceVersionV = {

      val (forceVersionErrors, forceVersions0) =
        Parse.moduleVersions(options.forceVersion, scalaVersion)

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

    val forcedPropertiesV = options
      .forceProperty
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

    val profiles = options.profile.toSet

    (maxIterationsV, forceVersionV, forcedPropertiesV).mapN {
      (maxIterations, forceVersion, forcedProperties) =>
        ResolutionParams(
          options.keepOptional,
          maxIterations,
          forceVersion,
          forcedProperties,
          profiles,
          options.typelevel
        )
    }
  }
}
