package coursier.cli.resolve

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.{CacheParams, DependencyParams, OutputParams, RepositoryParams}
import coursier.params.ResolutionParams
import coursier.parse.{JavaOrScalaModule, ModuleParser}
import coursier.version.VersionConstraint

final case class SharedResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: RepositoryParams,
  dependency: DependencyParams,
  resolution: ResolutionParams,
  classpathOrder: Option[Boolean]
) {
  def updatedResolution(scalaVersionOpt: Option[VersionConstraint]): ResolutionParams =
    resolution
      .withScalaVersionOpt0(resolution.scalaVersionOpt0.flatMap(_ => scalaVersionOpt))
      .withExclusions(
        dependency.exclude
          .map { m =>
            val m0 = m.module(scalaVersionOpt.getOrElse(VersionConstraint.empty).asString)
            (m0.organization, m0.name)
          }
      )
}

object SharedResolveParams {
  def apply(options: SharedResolveOptions): ValidatedNel[String, SharedResolveParams] = {

    val cacheV        = options.cacheOptions.params
    val outputV       = OutputParams(options.outputOptions)
    val repositoriesV =
      RepositoryParams(options.repositoryOptions, options.dependencyOptions.sbtPlugin.nonEmpty)
    val resolutionV = options.resolutionOptions.params
    val dependencyV =
      DependencyParams(
        options.dependencyOptions,
        resolutionV.toOption.flatMap(_.scalaVersionOpt0).map(_.asString)
      )

    val classpathOrder = options.classpathOrder

    (cacheV, outputV, repositoriesV, dependencyV, resolutionV).mapN {
      (cache, output, repositories, dependency, resolution) =>
        SharedResolveParams(
          cache,
          output,
          repositories,
          dependency,
          resolution,
          classpathOrder
        )
    }
  }
}
