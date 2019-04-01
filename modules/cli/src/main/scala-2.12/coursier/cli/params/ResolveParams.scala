package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.ResolveOptions
import coursier.cli.params.shared.{DependencyParams, OutputParams, RepositoryParams}
import coursier.core.{Module, Repository}
import coursier.params.{CacheParams, ResolutionParams}
import coursier.parse.ModuleParser

final case class ResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  dependency: DependencyParams,
  resolution: ResolutionParams,
  benchmark: Int,
  benchmarkCache: Boolean,
  tree: Boolean,
  reverseTree: Boolean,
  whatDependsOn: Set[Module],
  conflicts: Boolean
) {
  def anyTree: Boolean =
    tree ||
      reverseTree ||
      whatDependsOn.nonEmpty
}

object ResolveParams {
  def apply(options: ResolveOptions): ValidatedNel[String, ResolveParams] = {

    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions, options.dependencyOptions.sbtPlugin.nonEmpty)
    val resolutionV = options.resolutionOptions.params
    val dependencyV = DependencyParams(
      resolutionV.toOption.fold(options.resolutionOptions.scalaVersionOrDefault)(_.selectedScalaVersion),
      options.dependencyOptions
    )

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree
    val whatDependsOnV =
      resolutionV.toOption.map(_.selectedScalaVersion) match {
        case None =>
          Validated.validNel(Nil)
        case Some(sv) =>
          options.whatDependsOn.traverse(
            ModuleParser.module(_, sv).toValidatedNel
          )
      }

    val conflicts = options.conflicts

    val printCheck =
      if (Seq(tree, reverseTree, options.whatDependsOn.nonEmpty, conflicts).count(identity) > 1)
        Validated.invalidNel(
          "Cannot specify several options among --tree, --reverse-tree, --what-depends-on, --conflicts"
        )
      else
        Validated.validNel(())

    val benchmarkCacheV =
      if (options.benchmark == 0 && options.benchmarkCache)
        Validated.invalidNel("Cannot specify --benchmark-cache without --benchmark")
      else
        Validated.validNel(options.benchmarkCache)

    (cacheV, outputV, repositoriesV, dependencyV, resolutionV, whatDependsOnV, printCheck, benchmarkCacheV).mapN {
      (cache, output, repositories, dependency, resolution, whatDependsOn, _, benchmarkCache) =>
        ResolveParams(
          cache,
          output,
          repositories,
          dependency,
          resolution,
          benchmark,
          benchmarkCache,
          tree,
          reverseTree,
          whatDependsOn.toSet,
          conflicts
        )
    }
  }
}
