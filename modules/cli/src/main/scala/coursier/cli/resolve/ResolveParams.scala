package coursier.cli.resolve

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.params.{DependencyParams, OutputParams, RepositoryParams}
import coursier.params.{CacheParams, ResolutionParams}
import coursier.parse.{JavaOrScalaModule, ModuleParser}

final case class ResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: RepositoryParams,
  dependency: DependencyParams,
  resolution: ResolutionParams,
  benchmark: Int,
  benchmarkCache: Boolean,
  tree: Boolean,
  reverseTree: Boolean,
  whatDependsOn: Seq[JavaOrScalaModule],
  conflicts: Boolean,
  classpathOrder: Boolean
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
    val dependencyV = DependencyParams(options.dependencyOptions, resolutionV.toOption.flatMap(_.scalaVersion))

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree
    val whatDependsOnV = options.whatDependsOn.traverse(
      ModuleParser.javaOrScalaModule(_).toValidatedNel
    )

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

    val classpathOrder = options.classpathOrder

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
          whatDependsOn,
          conflicts,
          classpathOrder
        )
    }
  }
}
