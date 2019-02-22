package coursier.cli.resolve

import java.io.PrintStream
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import cats.implicits._
import coursier.cache.ProgressBarLogger
import coursier.Resolution
import coursier.cli.options.ResolveOptions
import coursier.cli.params.ResolveParams
import coursier.cli.scaladex.Scaladex
import coursier.core.{Dependency, Module, ResolutionProcess}
import coursier.error.ResolutionError
import coursier.graph.Conflict
import coursier.util._

import scala.concurrent.ExecutionContext

object Resolve extends CaseApp[ResolveOptions] {

  /**
    * Tries to parse get dependencies via Scala Index lookups.
    */
  def handleScaladexDependencies(
    params: ResolveParams,
    pool: ExecutorService
  ): Task[List[Dependency]] =
    if (params.dependency.scaladexLookups.isEmpty)
      Task.point(Nil)
    else {

      val logger = params.output.logger()

      val scaladex = Scaladex.withCache(params.cache.cache(pool, logger).fetch)

      val tasks = params.dependency.scaladexLookups.map { s =>
        Dependencies.handleScaladexDependency(s, params.resolution.selectedScalaVersion, scaladex, params.output.verbosity)
          .map {
            case Left(error) => Validated.invalidNel(error)
            case Right(l) => Validated.validNel(l)
          }
      }

      val task = Gather[Task].gather(tasks)
        .flatMap(_.toList.flatSequence match {
          case Validated.Valid(l) =>
            Task.point(l)
          case Validated.Invalid(errs) =>
            Task.fail(
              new ResolveException(
                s"Error during Scaladex lookups:\n" +
                  errs.toList.map("  " + _).mkString("\n")
              )
            )
        })

      for {
        _ <- Task.delay(logger.init(()))
        e <- task.attempt
        _ <- Task.delay(logger.stop())
        t <- Task.fromEither(e)
      } yield t
    }

  private def benchmark[T](iterations: Int): Task[T] => Task[T] = { run =>

    val res =
      for {
        start <- Task.delay(System.currentTimeMillis())
        res0 <- run
        end <- Task.delay(System.currentTimeMillis())
        _ <- Task.delay {
          Console.err.println(s"${end - start} ms")
        }
      } yield res0

    def result(warmUp: Int): Task[T] =
      if (warmUp >= iterations)
        for {
          _ <- Task.delay(Console.err.println("Benchmark resolution"))
          r <- res
        } yield r
      else
        for {
          _ <- Task.delay(Console.err.println(s"Warm-up ${warmUp + 1} / $iterations"))
          _ <- res
          r <- result(warmUp + 1)
        } yield r

    result(0)
  }

  // Roughly runs two kinds of side effects under the hood: printing output and asking things to the cache
  def task(
    params: ResolveParams,
    pool: ExecutorService,
    // stdout / stderr not used everywhere (added mostly for testing)
    stdout: PrintStream,
    stderr: PrintStream,
    args: Seq[String],
    printOutput: Boolean = true,
    force: Boolean = false
  ): Task[Resolution] = {

    val depsAndReposOrError = for {
      depsExtraRepoOpt <- Dependencies.withExtraRepo(
        args,
        params.resolution.selectedScalaVersion,
        params.dependency.defaultConfiguration,
        params.cache.cacheLocalArtifacts,
        params.dependency.intransitiveDependencies ++ params.dependency.sbtPluginDependencies
      )
      (deps, extraRepoOpt) = depsExtraRepoOpt
      deps0 = Dependencies.addExclusions(
        deps,
        params.dependency.exclude,
        params.dependency.perModuleExclude
      )
      // Prepend FallbackDependenciesRepository to the repository list
      // so that dependencies with URIs are resolved against this repo
      repositories = extraRepoOpt.toSeq ++ params.repositories
      _ <- {
        val invalidForced = extraRepoOpt
          .map(_.fallbacks.toSeq)
          .getOrElse(Nil)
          .collect {
            case ((mod, version), _) if params.resolution.forceVersion.get(mod).exists(_ != version) =>
              (mod, version)
          }
        if (invalidForced.isEmpty)
          Right(())
        else
          Left(
            new ResolveException(
              s"Cannot force a version that is different from the one specified " +
                s"for modules ${invalidForced.map { case (mod, ver) => s"$mod:$ver" }.mkString(", ")} with url"
            )
          )
      }

    } yield (deps0, repositories)

    for {
      t <- Task.fromEither(depsAndReposOrError)
      (deps, repositories) = t

      scaladexDeps <- handleScaladexDependencies(params, pool)

      deps0 = deps ++ scaladexDeps

      _ = Output.printDependencies(params.output, params.resolution, deps0, stdout, stderr)

      resAndWarnings <- coursier.Resolve.resolveIOWithConflicts(
        deps0,
        repositories,
        params.resolution,
        cache = params.cache.cache(
          pool,
          params.output.logger(),
          inMemoryCache = params.benchmark != 0 && params.benchmarkCache
        ),
        through = {
          t: Task[Resolution] =>
            if (params.benchmark == 0) t
            else
              benchmark(math.abs(params.benchmark))(t)
        },
        transformFetcher = {
          f: ResolutionProcess.Fetch[Task] =>
            if (params.output.verbosity >= 2) {
              modVers: Seq[(Module, String)] =>
                val print = Task.delay {
                  Output.errPrintln(s"Getting ${modVers.length} project definition(s)")
                }

                print.flatMap(_ => f(modVers))
            } else
              f
        }
      ).attempt.flatMap {
        case Left(ex: ResolutionError) =>
          if (force || params.output.forcePrint)
            Task.point((ex.resolution, Nil, ex.errors))
          else
            Task.fail(new ResolveException("Resolution error: " + ex.getMessage, ex))
        case e =>
          Task.fromEither(e.map { case (r, w) => (r, w, Nil) })
      }

      (res, _, errors) = resAndWarnings // TODO Print warnings
      valid = errors.isEmpty

      _ = {
        val outputToStdout = printOutput && (valid || params.output.forcePrint)
        if (outputToStdout || params.output.verbosity >= 2) {
          Output.printResolutionResult(
            printResultStdout = outputToStdout,
            params,
            res,
            stdout,
            stderr,
            colors = !ProgressBarLogger.defaultFallbackMode
          )
        }
      }

      _ = {
        for (err <- errors)
          stderr.println(err.getMessage)
      }
    } yield res
  }


  def run(options: ResolveOptions, args: RemainingArgs): Unit =
    ResolveParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Schedulable.fixedThreadPool(params.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, System.out, System.err, args.all)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) if params.output.verbosity <= 1 =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e) => throw e
          case Right(_) =>
        }
    }

}
