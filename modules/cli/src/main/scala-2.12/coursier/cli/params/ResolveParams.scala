package coursier.cli.params

import java.io.File

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.options.ResolveOptions
import coursier.cli.params.shared.{CacheParams, OutputParams, RepositoryParams, ResolutionParams}
import coursier.core.Repository

final case class ResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  resolution: ResolutionParams,
  benchmark: Int,
  tree: Boolean,
  reverseTree: Boolean
)

object ResolveParams {
  def apply(options: ResolveOptions): ValidatedNel[String, ResolveParams] = {

    val cacheV = CacheParams(options.cacheOptions)
    val outputV = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions)
    val resolutionV = ResolutionParams(options.resolutionOptions)

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree

    (cacheV, outputV, repositoriesV, resolutionV).mapN {
      (cache, output, repositories, resolution) =>
        ResolveParams(
          cache,
          output,
          repositories,
          resolution,
          benchmark,
          tree,
          reverseTree
        )
    }
  }
}
