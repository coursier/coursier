package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.ResolveOptions
import coursier.cli.params.shared._
import coursier.core.{Module, Repository}
import coursier.util.Parse

final case class ResolveParams(
  cache: CacheParams,
  output: OutputParams,
  repositories: Seq[Repository],
  dependency: DependencyParams,
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
    val repositoriesV = RepositoryParams(options.repositoryOptions, options.dependencyOptions.sbtPlugin.nonEmpty)
    val dependencyV = DependencyParams(options.dependencyOptions)
    val resolutionV = ResolutionParams(
      options.resolutionOptions,
      dependencyV.toOption.fold(options.dependencyOptions.scalaVersion)(_.scalaVersion)
    )

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree
    val whatDependsOnV =
      dependencyV.toOption.map(_.scalaVersion) match {
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

    (cacheV, outputV, repositoriesV, dependencyV, resolutionV, whatDependsOnV, treeCheck, treeWhatDependsOnCheck).mapN {
      (cache, output, repositories, dependency, resolution, whatDependsOn, _, _) =>
        ResolveParams(
          cache,
          output,
          repositories,
          dependency,
          resolution,
          benchmark,
          tree,
          reverseTree,
          whatDependsOn.toSet
        )
    }
  }
}
