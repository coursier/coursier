package coursier.cli.resolve

import java.net.{URL, URLDecoder}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.FallbackDependenciesRepository
import coursier.cli.scaladex.Scaladex
import coursier.core.{Configuration, Dependency, ModuleName, Organization}
import coursier.util.{Parse, Task}
import coursier.util.Parse.ModuleRequirements

object Dependencies {

  /**
    * Parses a simple dependency, like "org.typelevel:cats-core_2.12:1.1.0".
    */
  def parseSimpleDependency(
    rawDependency: String,
    scalaVersion: String,
    defaultConfiguration: Configuration
  ): Either[String, (Dependency, Map[String, String])] =
    Parse.moduleVersionConfig(
      rawDependency,
      ModuleRequirements(defaultConfiguration = defaultConfiguration),
      transitive = true,
      scalaVersion
    )

  /**
    * Tries to get a dependency, like "scalafmt", via a Scala Index lookup.
    */
  def handleScaladexDependency(
    rawDependency: String,
    scalaVersion: String,
    scaladex: Scaladex[Task],
    verbosity: Int
  ): Task[Either[String, List[Dependency]]] = {

    val deps = scaladex.dependencies(
      rawDependency,
      scalaVersion,
      if (verbosity >= 2) Console.err.println(_) else _ => ()
    )

    deps.map { modVers =>
      val m = modVers.groupBy(_._2)
      if (m.size > 1) {
        val (keptVer, modVers0) = m
          .map {
            case (v, l) =>
              val ver = coursier.core.Parse.version(v)
                .getOrElse(???) // FIXME

              ver -> l
          }
          .maxBy(_._1)

        if (verbosity >= 1)
          Console.err.println(s"Keeping version ${keptVer.repr}")

        modVers0
      } else
        modVers
    }.run.map(_.right.map { modVers =>
      modVers.toList.map {
        case (mod, ver) =>
          coursier.Dependency(mod, ver)
      }
    })
  }

  /**
    * Tries to parse dependencies as a simple dependencies.
    */
  def handleDependencies(
    rawDependencies: Seq[String],
    scalaVersion: String,
    defaultConfiguration: Configuration
  ): ValidatedNel[String, List[(Dependency, Map[String, String])]] =
    rawDependencies
      .map { s =>
        val e = parseSimpleDependency(
          s,
          scalaVersion,
          defaultConfiguration
        )
        e match {
          case Left(error) => Validated.invalidNel(error)
          case Right(d) => Validated.validNel(List(d))
        }
      }
      .toList
      .flatSequence

  def withExtraRepo(
    rawDependencies: Seq[String],
    scalaVersion: String,
    defaultConfiguration: Configuration,
    cacheLocalArtifacts: Boolean,
    extraDependencies: Seq[(Dependency, Map[String, String])]
  ): Either[Throwable, (List[Dependency], Option[FallbackDependenciesRepository])] =
    handleDependencies(rawDependencies, scalaVersion, defaultConfiguration) match {
      case Validated.Valid(l) =>

        val l0 = l ++ extraDependencies

        val deps = l0.map(_._1)

        val extraRepoOpt = {

          // Any dependencies with URIs should not be resolved with a pom so this is a
          // hack to add all the deps with URIs to the FallbackDependenciesRepository
          // which will be used during the resolve
          val m = l0.flatMap {
            case (dep, extraParams) =>
              extraParams.get("url").map { url =>
                dep.moduleVersion -> new URL(URLDecoder.decode(url, "UTF-8"))
              }
          }.toMap

          if (m.isEmpty)
            None
          else
            Some(
              FallbackDependenciesRepository(
                m.mapValues((_, true)),
                cacheLocalArtifacts
              )
            )
        }

        Right((deps, extraRepoOpt))

      case Validated.Invalid(err) =>
        Left(new ResolveException(
          "Error processing dependencies:\n" +
            err.toList.map("  " + _).mkString("\n")
        ))
    }

  def addExclusions(
    dep: Dependency,
    exclude: Set[(Organization, ModuleName)],
    perModuleExclude: Map[String, Set[(Organization, ModuleName)]],
  ): Dependency =
    dep.copy(
      exclusions = dep.exclusions |
        perModuleExclude.getOrElse(dep.module.orgName, Set()) |
        exclude
    )

  def addExclusions(
    deps: Seq[Dependency],
    exclude: Set[(Organization, ModuleName)],
    perModuleExclude: Map[String, Set[(Organization, ModuleName)]],
  ): Seq[Dependency] =
    deps.map { dep =>
      addExclusions(dep, exclude, perModuleExclude)
    }

}
