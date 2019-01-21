package coursier.cli.fetch

import java.io.{File, PrintStream}
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import coursier.cli.Helper
import coursier.cli.options.{CommonOptions, FetchOptions}
import coursier.cli.params.FetchParams
import coursier.cli.resolve.{Output, ResolveException}
import coursier.util.{Schedulable, Task}

import scala.concurrent.ExecutionContext

final class Fetch(options: FetchOptions, args: RemainingArgs) {

  val commonOpt = CommonOptions(
    benchmark = options.resolveOptions.benchmark,
    tree = options.resolveOptions.tree,
    reverseTree = options.resolveOptions.reverseTree,
    jsonOutputFile = options.jsonOutputFile,
    cacheOptions = options.resolveOptions.cacheOptions,
    repositoryOptions = options.resolveOptions.repositoryOptions,
    resolutionOptions = options.resolveOptions.resolutionOptions,
    dependencyOptions = options.resolveOptions.dependencyOptions,
    outputOptions = options.resolveOptions.outputOptions
  )


  val params: FetchParams = ???
  val pool = Schedulable.fixedThreadPool(params.resolve.cache.parallel)
  val ec = ExecutionContext.fromExecutorService(pool)

  val t = coursier.cli.resolve.Resolve.task(params.resolve, pool, System.out, System.err, args.all)

  t.attempt.unsafeRun()(ec) match {
    case Left(e: ResolveException) =>
      Output.errPrintln(e.message)
      sys.exit(1)
    case Left(e) => throw e
    case Right(_) =>
  }

  val helper = new Helper(commonOpt, args.all, ignoreErrors = options.artifactOptions.forceFetch)

  val files0 = helper.fetch(
    sources = options.artifactOptions.sources,
    javadoc = options.artifactOptions.javadoc,
    default = options.artifactOptions.default0,
    classifier0 = options.artifactOptions.classifier0,
    artifactTypes = options.artifactOptions.artifactTypes
  )

}

object Fetch extends CaseApp[FetchOptions] {

  def apply(options: FetchOptions, args: RemainingArgs): Fetch =
    new Fetch(options, args)

  def task(options: FetchOptions, params: FetchParams, pool: ExecutorService, stdout: PrintStream, stderr: PrintStream, args: Seq[String]): Task[Seq[File]] = {

    // params should be used instead of options once this is refactored…

    val commonOpt = CommonOptions(
      benchmark = options.resolveOptions.benchmark,
      tree = options.resolveOptions.tree,
      reverseTree = options.resolveOptions.reverseTree,
      jsonOutputFile = options.jsonOutputFile,
      cacheOptions = options.resolveOptions.cacheOptions,
      repositoryOptions = options.resolveOptions.repositoryOptions,
      resolutionOptions = options.resolveOptions.resolutionOptions,
      dependencyOptions = options.resolveOptions.dependencyOptions,
      outputOptions = options.resolveOptions.outputOptions
    )

    Task.delay {
      val helper = new Helper(commonOpt, args, ignoreErrors = options.artifactOptions.forceFetch)

      helper.fetch(
        sources = options.artifactOptions.sources,
        javadoc = options.artifactOptions.javadoc,
        default = options.artifactOptions.default0,
        classifier0 = options.artifactOptions.classifier0,
        artifactTypes = options.artifactOptions.artifactTypes
      )
    }
  }

  def run(options: FetchOptions, args: RemainingArgs): Unit =
    FetchParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Schedulable.fixedThreadPool(params.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(options, params, pool, System.out, System.err, args.all)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e) => throw e
          case Right(files) =>
            // Some progress lines seem to be scraped without this.
            Console.out.flush()

            val out =
              if (options.classpath)
                files
                  .map(_.toString)
                  .mkString(File.pathSeparator)
              else
                files
                  .map(_.toString)
                  .mkString("\n")

            println(out)
        }
    }

}
