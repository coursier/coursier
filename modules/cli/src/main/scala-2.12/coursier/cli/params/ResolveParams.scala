package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.ResolveOptions
import coursier.cli.params.shared.{CacheParams, OutputParams, RepositoryParams, ResolutionParams}
import coursier.core.{Module, Repository}
import coursier.util.Parse

final case class ResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  resolution: ResolutionParams,
  benchmark: Int,
  tree: Boolean,
  reverseTree: Boolean,
  whatDependsOn: Set[Module]
) {
  def anyTree: Boolean =
    tree ||
      reverseTree ||
      whatDependsOn.nonEmpty
}

object ResolveParams {
  def apply(options: ResolveOptions): ValidatedNel[String, ResolveParams] = {

    val cacheV = CacheParams(options.cacheOptions)
    val outputV = OutputParams(options.outputOptions)
    val repositoriesV = RepositoryParams(options.repositoryOptions, options.resolutionOptions.sbtPlugin.nonEmpty)
    val resolutionV = ResolutionParams(options.resolutionOptions)

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree
    val whatDependsOnV =
      resolutionV.toOption.map(_.scalaVersion) match {
        case None =>
          Validated.validNel(Nil)
        case Some(sv) =>
          options.whatDependsOn.traverse(
            Parse.module(_, sv).toValidatedNel
          )
      }

    val treeCheck =
      if (tree && reverseTree)
        Validated.invalidNel("Cannot specify both --tree and --reverse-tree")
      else
        Validated.validNel(())

    val treeWhatDependsOnCheck =
      if ((tree || reverseTree) && options.whatDependsOn.nonEmpty)
        Validated.invalidNel("Cannot specify --what-depends-on along with --tree or --reverse-tree")
      else
        Validated.validNel(())

    (cacheV, outputV, repositoriesV, resolutionV, whatDependsOnV, treeCheck, treeWhatDependsOnCheck).mapN {
      (cache, output, repositories, resolution, whatDependsOn, _, _) =>
        ResolveParams(
          cache,
          output,
          repositories,
          resolution,
          benchmark,
          tree,
          reverseTree,
          whatDependsOn.toSet
        )
    }
  }
}
