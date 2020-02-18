package coursier.cli.resolve

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.parse.{JavaOrScalaModule, ModuleParser}

final case class ResolveParams(
  shared: SharedResolveParams,
  benchmark: Int,
  benchmarkCache: Boolean,
  tree: Boolean,
  reverseTree: Boolean,
  whatDependsOn: Seq[JavaOrScalaModule],
  candidateUrls: Boolean,
  conflicts: Boolean,
  forcePrint: Boolean
) {

  def cache = shared.cache
  def output = shared.output
  def repositories = shared.repositories
  def dependency = shared.dependency
  def resolution = shared.resolution
  def classpathOrder = shared.classpathOrder

  def anyTree: Boolean =
    tree ||
      reverseTree ||
      whatDependsOn.nonEmpty
}

object ResolveParams {
  def apply(options: ResolveOptions): ValidatedNel[String, ResolveParams] = {

    val sharedV = SharedResolveParams(options.sharedResolveOptions)

    val benchmark = options.benchmark
    val tree = options.tree
    val reverseTree = options.reverseTree
    val whatDependsOnV = options.whatDependsOn.traverse(
      ModuleParser.javaOrScalaModule(_).toValidatedNel
    )
    val candidateUrls = options.candidateUrls

    val conflicts = options.conflicts

    val printCheck =
      if (Seq(tree, reverseTree, options.whatDependsOn.nonEmpty, conflicts, candidateUrls).count(identity) > 1)
        Validated.invalidNel(
          "Cannot specify several options among --tree, --reverse-tree, --what-depends-on, --conflicts, --candidate-urls"
        )
      else
        Validated.validNel(())

    val benchmarkCacheV =
      if (options.benchmark == 0 && options.benchmarkCache)
        Validated.invalidNel("Cannot specify --benchmark-cache without --benchmark")
      else
        Validated.validNel(options.benchmarkCache)

    val forcePrint = options.forcePrint

    (sharedV, whatDependsOnV, printCheck, benchmarkCacheV).mapN {
      (shared, whatDependsOn, _, benchmarkCache) =>
        ResolveParams(
          shared,
          benchmark,
          benchmarkCache,
          tree,
          reverseTree,
          whatDependsOn,
          candidateUrls,
          conflicts,
          forcePrint
        )
    }
  }
}
