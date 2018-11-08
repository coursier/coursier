package coursier.cli.resolve

import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.{Cache, Resolution, TermDisplay}
import coursier.Cache.Logger
import coursier.cli.options.ResolveOptions
import coursier.cli.params.ResolveParams
import coursier.cli.params.shared.{CacheParams, OutputParams}
import coursier.cli.scaladex.Scaladex
import coursier.core.{Module, Repository, ResolutionProcess}
import coursier.extra.Typelevel
import coursier.util._

import scala.concurrent.ExecutionContext

object Resolve extends CaseApp[ResolveOptions] {

  private def withLogger[T](params: OutputParams)(f: Option[Logger] => Task[T]): Task[T] = {

    val loggerFallbackMode =
      !params.progressBars && TermDisplay.defaultFallbackMode

    val logger =
      if (params.verbosity >= 0)
        Some(
          new TermDisplay(
            new OutputStreamWriter(System.err),
            fallbackMode = loggerFallbackMode
          )
        )
      else
        None

    val init = Task.delay {
      logger.foreach(_.init())
    }

    val stop = Task.delay {
      logger.foreach(_.stop())
    }

    for {
      _ <- init
      e <- f(logger).attempt
      _ <- stop
      t <- Task.fromEither(e)
    } yield t
  }

  private def fetchs(params: CacheParams, pool: ExecutorService, logger: Option[Cache.Logger]): Seq[coursier.Fetch.Content[Task]] =
    params
      .cachePolicies
      .map { p =>
        Cache.fetch[Task](params.cache, p, checksums = Nil, logger = logger, pool = pool, ttl = params.ttl)
      }

  private def runResolution(
    params: ResolveParams,
    repositories: Seq[Repository],
    startRes: Resolution,
    pool: ExecutorService,
    logger: Option[Logger]
  ): Task[Resolution] = {

    val fetch0 = {

      val fetchQuiet = {
        val fetchs0 = fetchs(params.cache, pool, logger)
        coursier.Fetch.from(
          repositories,
          fetchs0.head,
          fetchs0.tail: _*
        )
      }

      if (params.output.verbosity >= 2) {
        modVers: Seq[(Module, String)] =>
          val print = Task.delay {
            Output.errPrintln(s"Getting ${modVers.length} project definition(s)")
          }

          print.flatMap(_ => fetchQuiet(modVers))
      } else
        fetchQuiet
    }

    if (params.benchmark > 0) {
      final class Counter(var value: Int = 0) {
        def add(value: Int): Unit = {
          this.value += value
        }
      }

      def timed[T](name: String, counter: Counter, f: Task[T]): Task[T] =
        Task.delay(System.currentTimeMillis()).flatMap { start =>
          f.map { t =>
            val end = System.currentTimeMillis()
            Console.err.println(s"$name: ${end - start} ms")
            counter.add((end - start).toInt)
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
        if (warmUp >= params.benchmark)
          for {
            _ <- Task.delay(Console.err.println("Benchmark resolution"))
            r <- res
          } yield r
        else
          for {
            _ <- Task.delay(Console.err.println(s"Warm-up ${warmUp + 1} / ${params.benchmark}"))
            _ <- res
            r <- result(warmUp + 1)
          } yield r

      result(0)
    } else if (params.benchmark < 0) {

      val res =
        for {
          start <- Task.delay(System.currentTimeMillis())
          res0 <- startRes
            .process
            .run(fetch0, params.resolution.maxIterations)
          end <- Task.delay(System.currentTimeMillis())
          _ <- Task.delay {
            Console.err.println(s"${end - start} ms")
          }
        } yield res0

      def result(warmUp: Int): Task[Resolution] =
        if (warmUp >= -params.benchmark)
          for {
            _ <- Task.delay(Console.err.println("Benchmark resolution"))
            r <- res
          } yield r
        else
          for {
            _ <- Task.delay(Console.err.println(s"Warm-up ${warmUp + 1} / ${-params.benchmark}"))
            _ <- res
            r <- result(warmUp + 1)
          } yield r

      result(0)
    } else
      startRes.process.run(fetch0, params.resolution.maxIterations)
  }

  private def validateResolution(res: Resolution, verbosity: Int): ValidatedNel[String, Unit] = {

    val checkDone =
      if (res.isDone)
        Validated.validNel(())
      else
        Validated.invalidNel("Maximum number of iterations reached!")

    val checkErrors = res
      .errors
      .map {
        case ((module, version), errors) =>
          s"$module:$version\n${errors.map("  " + _.replace("\n", "  \n")).mkString("\n")}"
      } match {
        case Seq() =>
          Validated.validNel(())
        case Seq(h, t @ _*) =>
          Validated.invalid(NonEmptyList(h, t.toList))
      }

    val checkConflicts =
      if (res.conflicts.isEmpty)
        Validated.validNel(())
      else
        Validated.invalidNel(
          s"\nConflict:\n" +
            Print.dependenciesUnknownConfigs(
              res.conflicts.toVector,
              res.projectCache.mapValues { case (_, p) => p },
              printExclusions = verbosity >= 1
            )
        )

    (checkDone, checkErrors, checkConflicts).mapN {
      (_, _, _) => ()
    }
  }

  // Roughly runs two kinds of side effects under the hood: printing output and asking things to the cache
  def task(
    params: ResolveParams,
    pool: ExecutorService,
    args: Seq[String]
  ): Task[(Resolution, Boolean)] =
    for {
      // parse dependencies, possibly doing some Scala Index lookups
      depsExtraRepoOpt <- withLogger(params.output) { logger =>

        // TODO Manage not to initialize logger if it's not used

        val scaladex0 = Scaladex.withCache(fetchs(params.cache, pool, logger): _*)

        Dependencies.withExtraRepo(
          args,
          scaladex0,
          params.resolution.scalaVersion,
          params.resolution.defaultConfiguration,
          params.output.verbosity,
          params.cache.cacheLocalArtifacts,
          params.resolution.intransitiveDependencies
        )
      }
      (deps0, extraRepoOpt) = depsExtraRepoOpt

      deps = Dependencies.addExclusions(
        deps0,
        params.resolution.exclude,
        params.resolution.perModuleExclude
      )

      _ <- {
        val invalidForced = extraRepoOpt
          .map(_.fallbacks.toSeq)
          .getOrElse(Nil)
          .collect {
            case ((mod, version), _) if params.resolution.forceVersion.get(mod).exists(_ != version) =>
              (mod, version)
          }
        if (invalidForced.isEmpty)
          Task.point(())
        else
          Task.fail(
            new ResolveException(
              s"Cannot force a version that is different from the one specified " +
                s"for modules ${invalidForced.map { case (mod, ver) => s"$mod:$ver" }.mkString(", ")} with url"
            )
          )
      }

      // Prepend FallbackDependenciesRepository to the repository list
      // so that dependencies with URIs are resolved against this repo
      repositories = extraRepoOpt.toSeq ++ params.repositories

      _ = Output.printDependencies(params.output, params.resolution, deps)

      startRes = Resolution(
        deps.toSet,
        forceVersions = params.resolution.forceVersion,
        filter = Some(dep => params.resolution.keepOptionalDependencies || !dep.optional),
        userActivations =
          if (params.resolution.profiles.isEmpty) None
          else Some(params.resolution.profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
        mapDependencies = if (params.resolution.typelevel) Some(Typelevel.swap(_)) else None,
        forceProperties = params.resolution.forcedProperties
      )

      res <- withLogger(params.output) { logger =>
        runResolution(
          params,
          repositories,
          startRes,
          pool,
          logger
        )
      }

      _ = Output.printResolutionResult(
        printResultStdout = true,
        params,
        deps,
        res
      )

      valid = validateResolution(res, params.output.verbosity) match {
        case Validated.Valid(()) => true
        case Validated.Invalid(errors) =>
          errors.toList.foreach(Output.errPrintln)
          false
      }
    } yield (res, valid)

  def run(options: ResolveOptions, args: RemainingArgs): Unit =
    ResolveParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Schedulable.fixedThreadPool(params.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, args.all)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) =>
            Output.errPrintln(e.message)
            sys.exit(1)
          case Left(e) => throw e
          case Right(_) =>
        }
    }

}
