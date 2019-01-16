package coursier.cli.resolve

import java.net.{URL, URLDecoder}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.FallbackDependenciesRepository
import coursier.cli.scaladex.Scaladex
import coursier.core.{Configuration, Dependency, ModuleName, Organization}
import coursier.util.{Gather, Parse, Task}
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
  ): Task[Either[String, List[(Dependency, Map[String, String])]]] = {

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
          (coursier.Dependency(mod, ver), Map.empty[String, String])
      }
    })
  }

  /**
    * Tries to parse a dependency as a simple dependency, or via a Scala Index lookup.
    */
  def handleDependency(
    rawDependency: String,
    scalaVersion: String,
    defaultConfiguration: Configuration,
    scaladex: Scaladex[Task],
    verbosity: Int
  ): Task[Either[String, List[(Dependency, Map[String, String])]]] =
    if (rawDependency.contains("/") || !rawDependency.contains(":"))
      handleScaladexDependency(
        rawDependency,
        scalaVersion,
        scaladex,
        verbosity
      )
    else
      Task.point(
         parseSimpleDependency(
           rawDependency,
           scalaVersion,
           defaultConfiguration
         ).right.map(List(_))
      )

  /**
    * Tries to parse dependencies as a simple dependencies, or via a Scala Index lookups.
    */
  def handleDependencies(
    rawDependencies: Seq[String],
    scalaVersion: String,
    defaultConfiguration: Configuration,
    scaladex: Scaladex[Task],
    verbosity: Int
  ): Task[ValidatedNel[String, List[(Dependency, Map[String, String])]]] = {

    val tasks = rawDependencies.map { s =>
      handleDependency(s, scalaVersion, defaultConfiguration, scaladex, verbosity)
        .map {
          case Left(error) => Validated.invalidNel(error)
          case Right(l) => Validated.validNel(l)
        }
    }

    Gather[Task].gather(tasks)
      .map(_.toList.flatSequence)
  }

  def withExtraRepo(
    rawDependencies: Seq[String],
    scaladex: Scaladex[Task],
    scalaVersion: String,
    defaultConfiguration: Configuration,
    verbosity: Int,
    cacheLocalArtifacts: Boolean,
    extraDependencies: Seq[(Dependency, Map[String, String])]
  ): Task[(List[Dependency], Option[FallbackDependenciesRepository])] = {
    handleDependencies(
      rawDependencies,
      scalaVersion,
      defaultConfiguration,
      scaladex,
      verbosity
    ).flatMap {
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

        Task.point((deps, extraRepoOpt))

      case Validated.Invalid(err) =>
        Task.fail(new ResolveException(
          "Error processing dependencies:\n" +
            err.toList.map("  " + _).mkString("\n")
        ))
    }
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
