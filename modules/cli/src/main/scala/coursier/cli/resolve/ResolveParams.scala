package coursier.cli.resolve

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.parse.{JavaOrScalaModule, ModuleParser}

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

final case class ResolveParams(
  shared: SharedResolveParams,
  benchmark: Int,
  benchmarkCache: Boolean,
  tree: Boolean,
  reverseTree: Boolean,
  whatDependsOn: Seq[JavaOrScalaModule],
  candidateUrls: Boolean,
  conflicts: Boolean,
  forcePrint: Boolean,
  retry: Option[(FiniteDuration, Int)]
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

    // TODO Validate that attempts > 0
    val retryV =
      (options.retry, options.attempts) match {
        case (Some(retry), attemptsOpt) =>
          duration(retry).map((_, attemptsOpt.getOrElse(30))).map(Some(_))
        case (None, Some(attempts)) =>
          Validated.validNel(Some((1.minute, attempts)))
        case (None, None) =>
          Validated.validNel(None)
      }

    (sharedV, whatDependsOnV, printCheck, benchmarkCacheV, retryV).mapN {
      (shared, whatDependsOn, _, benchmarkCache, retry) =>
        ResolveParams(
          shared,
          benchmark,
          benchmarkCache,
          tree,
          reverseTree,
          whatDependsOn,
          candidateUrls,
          conflicts,
          forcePrint,
          retry
        )
    }
  }

  private def duration(input: String): ValidatedNel[String, FiniteDuration] =
    try {
      Duration(input) match {
        case f: FiniteDuration => Validated.validNel(f)
        case _ => Validated.invalidNel(s"Invalid non-finite duration '$input'")
      }
    } catch {
      case _: IllegalArgumentException =>
        Validated.invalidNel(s"Invalid duration '$input'") // anything interesting in the exception message?
    }
}
