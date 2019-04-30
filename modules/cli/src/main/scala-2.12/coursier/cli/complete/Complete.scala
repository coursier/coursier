package coursier.cli.complete

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.cli.options.CompleteOptions
import coursier.cli.params.CompleteParams
import coursier.util.Sync

import scala.concurrent.ExecutionContext

object Complete extends CaseApp[CompleteOptions] {

  def run(options: CompleteOptions, args: RemainingArgs): Unit = {

    val params = CompleteParams(options, args).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) =>
        params0
    }

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)
    val cache = params.cache.cache(pool, params.output.logger())

    val result = coursier.complete.Complete(cache)
      .withRepositories(params.repositories)
      .withScalaVersion(params.scalaVersion)
      .withScalaBinaryVersion(params.scalaBinaryVersion)
      .withInput(params.toComplete)
      .result()
      .unsafeRun()(ec)

    if (params.output.verbosity >= 2)
      System.err.println(s"Completing ${result.input}")

    if (params.output.verbosity >= 1)
      result.results.foreach {
        case (repo, Left(err)) =>
          System.err.println(s"Error when completing with $repo:")
          System.err.println(err)
          if (params.output.verbosity >= 2)
            err.printStackTrace(System.err)
        case _ =>
      }

    val completions = result.completions

    if (params.output.verbosity >= 1)
      System.err.println(s"  Got ${completions.length} completion(s):")

    for (c <- completions)
      println(c)
  }

}
