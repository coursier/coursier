package coursier.cli.resolve

import java.io.PrintStream
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import cats.implicits._
import coursier.cache.CacheLogger
import coursier.Resolution
import coursier.cli.options.ResolveOptions
import coursier.cli.params.ResolveParams
import coursier.cli.scaladex.Scaladex
import coursier.core.{Dependency, Module, Repository, ResolutionProcess}
import coursier.extra.Typelevel
import coursier.internal.InMemoryCachingFetcher
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
        Dependencies.handleScaladexDependency(s, params.dependency.scalaVersion, scaladex, params.output.verbosity)
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
        _ <- Task.delay(logger.stopDidPrintSomething())
        t <- Task.fromEither(e)
      } yield t
    }

  private def runDetailedBenchmark(
    params: ResolveParams,
    startRes: Resolution,
    fetch0: ResolutionProcess.Fetch[Task],
    iterations: Int
  ): Task[Resolution] = {

    final class Counter(var value: Int = 0) {
      def add(value: Int): Unit = {
        this.value += value
      }
    }

    def timed[T](name: String, counter: Counter, f: Task[T]): Task[T] =
      Task.delay(System.nanoTime()).flatMap { start =>
        f.map { t =>
          val end = System.nanoTime()
          Console.err.println(s"$name: ${(end - start).toDouble / 1000000L} ms")
          counter.add(((end - start) / 1000000L).toInt)
          t
        }
      }

    def helper(proc: ResolutionProcess, counter: Counter, iteration: Int): Task[Resolution] =
      if (iteration >= params.resolution.maxIterations)
        Task.point(proc.current)
      else
        proc match {
          case _: coursier.core.Done =>
            Task.point(proc.current)
          case _ =>
            val iterationType = proc match {
              case _: coursier.core.Missing => "IO"
              case _: coursier.core.Continue => "calculations"
              case _ => ???
            }

            timed(
              s"Iteration ${iteration + 1} ($iterationType)",
              counter,
              proc.next(fetch0, fastForward = false)).flatMap(helper(_, counter, iteration + 1)
            )
        }

    val res =
      for {
        iterationCounter <- Task.delay(new Counter)
        resolutionCounter <- Task.delay(new Counter)
        res0 <- timed(
          "Resolution",
          resolutionCounter,
          helper(
            startRes.process,
            iterationCounter,
            0
          )
        )
        _ <- Task.delay {
          Console.err.println(s"Overhead: ${resolutionCounter.value - iterationCounter.value} ms")
        }
      } yield res0

    def result(warmUp: Int): Task[Resolution] =
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

  private def runSimpleBenchmark(
    params: ResolveParams,
    startRes: Resolution,
    logger: CacheLogger,
    fetch0: ResolutionProcess.Fetch[Task],
    iterations: Int
  ): Task[Resolution] = {

    val res =
      for {
        start <- Task.delay(System.currentTimeMillis())
        res0 <- coursier.Resolve.runProcess(startRes, fetch0, params.resolution.maxIterations, logger)
        end <- Task.delay(System.currentTimeMillis())
        _ <- Task.delay {
          Console.err.println(s"${end - start} ms")
        }
      } yield res0

    def result(warmUp: Int): Task[Resolution] =
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

  private def runResolution(
    params: ResolveParams,
    repositories: Seq[Repository],
    startRes: Resolution,
    pool: ExecutorService
  ): Task[Resolution] = {

    val logger = params.output.logger()

    val fetch0 = {

      val f = params.cache.cache(pool, logger).fetch
      val f0 =
        if (params.benchmark != 0 && params.benchmarkCache)
          new InMemoryCachingFetcher(f).fetcher
        else
          f
      val fetchQuiet = ResolutionProcess.fetch(repositories, f0)

      if (params.output.verbosity >= 2) {
        modVers: Seq[(Module, String)] =>
          val print = Task.delay {
            Output.errPrintln(s"Getting ${modVers.length} project definition(s)")
          }

          print.flatMap(_ => fetchQuiet(modVers))
      } else
        fetchQuiet
    }

    if (params.benchmark > 0)
      // init / stop logger?
      runDetailedBenchmark(params, startRes, fetch0, params.benchmark)
    else if (params.benchmark < 0)
      runSimpleBenchmark(params, startRes, logger, fetch0, -params.benchmark)
    else
      coursier.Resolve.runProcess(startRes, fetch0, params.resolution.maxIterations, logger)
  }

  // Roughly runs two kinds of side effects under the hood: printing output and asking things to the cache
  def task(
    params: ResolveParams,
    pool: ExecutorService,
    // stdout / stderr not used everywhere (added mostly for testing)
    stdout: PrintStream,
    stderr: PrintStream,
    args: Seq[String]
  ): Task[(Resolution, Boolean)] = {

    val e = for {
      // parse dependencies, possibly doing some Scala Index lookups
      depsExtraRepoOpt <- Dependencies.withExtraRepo(
        args,
        params.dependency.scalaVersion,
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
      t <- Task.fromEither(e)
      (deps, repositories) = t

      scaladexDeps <- handleScaladexDependencies(params, pool)

      deps0 = deps ++ scaladexDeps

      _ = Output.printDependencies(params.output, params.resolution, deps0, stdout, stderr)

      startRes = coursier.Resolve.initialResolution(deps0, params.resolution).copy(
        mapDependencies = if (params.resolution.typelevel) Some(Typelevel.swap(_)) else None
      )
      res <- runResolution(
        params,
        repositories,
        startRes,
        pool
      )

      validated = coursier.Resolve.validate(res, params.output.verbosity >= 1).either

      valid = validated.isRight

      _ = if (valid || params.output.forcePrint) {
        Output.printResolutionResult(
          printResultStdout = true,
          params,
          deps0,
          res,
          stdout,
          stderr
        )
      }

      _ = validated match {
        case Right(()) =>
        case Left(errors) =>
          stderr.println("Error:")
          errors.foreach(stderr.println)
      }
    } yield (res, valid)
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
          case Left(e: ResolveException) =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e) => throw e
          case Right(_) =>
        }
    }

}
