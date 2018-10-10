package coursier.cli.params.shared

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.shared.ResolutionOptions
import coursier.core._
import coursier.util.Parse
import coursier.util.Parse.ModuleRequirements

import scala.io.Source

final case class ResolutionParams(
  keepOptionalDependencies: Boolean,
  maxIterations: Int,
  forceVersion: Map[Module, String],
  forcedProperties: Map[String, String],
  exclude: Set[(Organization, ModuleName)],
  perModuleExclude: Map[String, Set[(Organization, ModuleName)]], // FIXME key should be Module
  scalaVersion: String,
  intransitiveDependencies: Seq[(Dependency, Map[String, String])],
  defaultConfiguration: Configuration,
  profiles: Set[String],
  typelevel: Boolean
)

object ResolutionParams {
  def apply(options: ResolutionOptions): ValidatedNel[String, ResolutionParams] = {

    val maxIterationsV =
      if (options.maxIterations > 0)
        Validated.validNel(options.maxIterations)
      else
        Validated.invalidNel(s"Max iteration must be > 0 (got ${options.maxIterations}")

    val forceVersionV = {

      val (forceVersionErrors, forceVersions0) =
        Parse.moduleVersions(options.forceVersion, options.scalaVersion)

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

    val excludeV = {

      val (excludeErrors, excludes0) = Parse.modules(options.exclude, options.scalaVersion)
      val (excludesNoAttr, excludesWithAttr) = excludes0.partition(_.attributes.isEmpty)

      if (excludeErrors.nonEmpty)
        Validated.invalidNel(
          s"Cannot parse excluded modules:\n" +
            excludeErrors
              .map("  " + _)
              .mkString("\n")
        )
      else if (excludesWithAttr.nonEmpty)
        Validated.invalidNel(
          s"Excluded modules with attributes not supported:\n" +
            excludesWithAttr
              .map("  " + _)
              .mkString("\n")
        )
      else
        Validated.validNel(
          excludesNoAttr
            .map(mod => (mod.organization, mod.name))
            .toSet
        )
    }

    val perModuleExcludeV =
      if (options.localExcludeFile.isEmpty)
        Validated.validNel(Map.empty[String, Set[(Organization, ModuleName)]])
      else {

        // meh, I/O

        val source = Source.fromFile(options.localExcludeFile) // default codec...
        val lines = try source.mkString.split("\n") finally source.close()

        lines
          .toList
          .traverse { str =>
            val parent_and_child = str.split("--")
            if (parent_and_child.length != 2)
              Validated.invalidNel(s"Failed to parse $str")
            else {
              val child_org_name = parent_and_child(1).split(":")
              if (child_org_name.length != 2)
                Validated.invalidNel(s"Failed to parse $child_org_name")
              else
                Validated.validNel((parent_and_child(0), (Organization(child_org_name(0)), ModuleName(child_org_name(1)))))
            }
          }
          .map { list =>
            list
              .groupBy(_._1)
              .mapValues(_.map(_._2).toSet)
              .iterator
              .toMap
          }
      }

    val scalaVersion = options.scalaVersion // TODO Validate that one a bit?

    val moduleReqV = (excludeV, perModuleExcludeV).mapN {
      (exclude, perModuleExclude) =>
        ModuleRequirements(exclude, perModuleExclude, options.defaultConfiguration0)
    }

    val intransitiveDependenciesV = moduleReqV
      .toEither
      .flatMap { moduleReq =>

        val (intransitiveModVerCfgErrors, intransitiveDepsWithExtraParams) =
          Parse.moduleVersionConfigs(options.intransitive, moduleReq, transitive = false, options.scalaVersion)

        if (intransitiveModVerCfgErrors.nonEmpty)
          Left(
            NonEmptyList.one(
              s"Cannot parse intransitive dependencies:\n" +
                intransitiveModVerCfgErrors.map("  "+_).mkString("\n")
            )
          )
        else
          Right(intransitiveDepsWithExtraParams)
      }
      .toValidated

    val defaultConfiguration = Configuration(options.defaultConfiguration)

    val profiles = options.profile.toSet

    val typelevel = options.typelevel

    (maxIterationsV, forceVersionV, forcedPropertiesV, excludeV, perModuleExcludeV, intransitiveDependenciesV).mapN {
      (maxIterations, forceVersion, forcedProperties, exclude, perModuleExclude, intransitiveDependencies) =>
        ResolutionParams(
          options.keepOptional,
          maxIterations,
          forceVersion,
          forcedProperties,
          exclude,
          perModuleExclude,
          scalaVersion,
          intransitiveDependencies,
          defaultConfiguration,
          profiles,
          typelevel
        )
    }
  }
}
