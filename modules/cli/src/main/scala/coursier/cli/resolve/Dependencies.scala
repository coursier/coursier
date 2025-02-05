package coursier.cli.resolve

import java.net.{URL, URLDecoder}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core.{
  Configuration,
  Dependency,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization
}
import coursier.parse.{DependencyParser, JavaOrScalaDependency, JavaOrScalaModule}
import coursier.version.{Version, VersionInterval}

object Dependencies {

  /** Tries to parse dependencies as a simple dependencies. */
  def handleDependencies(
    rawDependencies: Seq[String]
  ): ValidatedNel[String, List[(JavaOrScalaDependency, Map[String, String])]] =
    rawDependencies
      .map { s =>
        DependencyParser.javaOrScalaDependencyParams(s) match {
          case Left(error) => Validated.invalidNel(error)
          case Right(d)    => Validated.validNel(List(d))
        }
      }
      .toList
      .flatSequence

  def withExtraRepo(
    rawDependencies: Seq[String],
    extraDependencies: Seq[(JavaOrScalaDependency, Map[String, String])]
  ): Either[
    Throwable,
    (List[JavaOrScalaDependency], Map[(JavaOrScalaModule, Version), URL])
  ] =
    handleDependencies(rawDependencies) match {
      case Validated.Valid(l) =>
        val l0 = l ++ extraDependencies

        val deps = l0.map(_._1)

        val extraRepo =
          // Any dependencies with URIs should not be resolved with a pom so this is a
          // hack to add all the deps with URIs to the FallbackDependenciesRepository
          // which will be used during the resolve
          l0.flatMap {
            case (dep, extraParams) =>
              extraParams.get("url").map { url =>
                if (
                  dep.versionConstraint.interval != VersionInterval.zero || dep.versionConstraint.latest.nonEmpty
                )
                  sys.error(
                    s"Invalid version ${dep.versionConstraint.asString}: expected simple version like 1.2.3, not an interval or latest.*"
                  )
                (
                  dep.module,
                  dep.versionConstraint.preferred.getOrElse(Version.zero)
                ) -> new URL(URLDecoder.decode(url, "UTF-8"))
              }
          }.toMap

        Right((deps, extraRepo))

      case Validated.Invalid(err) =>
        Left(new ResolveException(
          "Error processing dependencies:" + System.lineSeparator() +
            err.toList.map("  " + _).mkString(System.lineSeparator())
        ))
    }

  def addExclusions(
    dep: Dependency,
    perModuleExclude: Map[Module, Set[Module]]
  ): Dependency =
    perModuleExclude.get(dep.module) match {
      case None => dep
      case Some(exclusions) =>
        dep.withMinimizedExclusions(
          dep.minimizedExclusions.join(MinimizedExclusions(exclusions.map(m =>
            (m.organization, m.name)
          )))
        )
    }

  def addExclusions(
    deps: Seq[Dependency],
    perModuleExclude: Map[Module, Set[Module]]
  ): Seq[Dependency] =
    deps.map { dep =>
      addExclusions(dep, perModuleExclude)
    }

}
