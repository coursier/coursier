package coursier.cli.params.shared

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.shared.DependencyOptions
import coursier.core._
import coursier.util.Parse
import coursier.util.Parse.ModuleRequirements

import scala.io.Source

final case class DependencyParams(
  exclude: Set[(Organization, ModuleName)],
  perModuleExclude: Map[String, Set[(Organization, ModuleName)]], // FIXME key should be Module
  intransitiveDependencies: Seq[(Dependency, Map[String, String])],
  sbtPluginDependencies: Seq[(Dependency, Map[String, String])],
  scaladexLookups: Seq[String],
  defaultConfiguration: Configuration
)

object DependencyParams {
  def apply(scalaVersion: String, options: DependencyOptions): ValidatedNel[String, DependencyParams] = {

    val excludeV = {

      val (excludeErrors, excludes0) = Parse.modules(options.exclude, scalaVersion)
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

    val moduleReqV = (excludeV, perModuleExcludeV).mapN {
      (exclude, perModuleExclude) =>
        ModuleRequirements(exclude, perModuleExclude, options.defaultConfiguration0)
    }

    val intransitiveDependenciesV = moduleReqV
      .toEither
      .flatMap { moduleReq =>

        val (intransitiveModVerCfgErrors, intransitiveDepsWithExtraParams) =
          Parse.moduleVersionConfigs(options.intransitive, moduleReq, transitive = false, scalaVersion)

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

    val sbtPluginDependenciesV = moduleReqV
      .toEither
      .flatMap { moduleReq =>

        val (sbtPluginModVerCfgErrors, sbtPluginDepsWithExtraParams) =
          Parse.moduleVersionConfigs(options.sbtPlugin, moduleReq, transitive = true, scalaVersion)

        if (sbtPluginModVerCfgErrors.nonEmpty)
          Left(
            NonEmptyList.one(
              s"Cannot parse sbt plugin dependencies:\n" +
                sbtPluginModVerCfgErrors.map("  "+_).mkString("\n")
            )
          )
        else if (sbtPluginDepsWithExtraParams.isEmpty)
          Right(Nil)
        else {
          val defaults = {
            val sbtVer = options.sbtVersion.split('.') match {
              case Array("1", _, _) =>
                // all sbt 1.x versions use 1.0 as short version
                "1.0"
              case arr => arr.take(2).mkString(".")
            }
            Map(
              "scalaVersion" -> scalaVersion.split('.').take(2).mkString("."),
              "sbtVersion" -> sbtVer
            )
          }
          val l = sbtPluginDepsWithExtraParams.map {
            case (dep, params) =>
              val dep0 = dep.copy(
                module = dep.module.copy(
                  attributes = defaults ++ dep.module.attributes // dependency specific attributes override the default values
                )
              )
              (dep0, params)
          }
          Right(l)
        }
      }
      .toValidated

    val defaultConfiguration = Configuration(options.defaultConfiguration)

    val scaladexLookups = options
      .scaladex
      .map(_.trim)
      .filter(_.nonEmpty)

    (excludeV, perModuleExcludeV, intransitiveDependenciesV, sbtPluginDependenciesV).mapN {
      (exclude, perModuleExclude, intransitiveDependencies, sbtPluginDependencies) =>
        DependencyParams(
          exclude,
          perModuleExclude,
          intransitiveDependencies,
          sbtPluginDependencies,
          scaladexLookups,
          defaultConfiguration
        )
    }
  }
}
