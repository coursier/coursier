package coursier.cli.resolve

import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService

import caseapp._
import cats.data.Validated
import cats.implicits._
import coursier.Resolution
import coursier.cache.Cache
import coursier.cache.loggers.RefreshLogger
import coursier.cli.app.{AppArtifacts, RawAppDescriptor}
import coursier.cli.install.{AppGenerator, Install}
import coursier.cli.scaladex.Scaladex
import coursier.core.{Dependency, Module, Repository}
import coursier.error.ResolutionError
import coursier.parse.JavaOrScalaDependency
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
        _ <- Task.delay(logger.init())
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

    val cache = params.cache.cache(
      pool,
      params.output.logger(),
      inMemoryCache = params.benchmark != 0 && params.benchmarkCache
    )

    val depsAndReposOrError = for {
      depsExtraRepoOpt <- Dependencies.withExtraRepo(
        args,
        params.dependency.defaultConfiguration,
        params.dependency.intransitiveDependencies ++ params.dependency.sbtPluginDependencies
      )
      (javaOrScalaDeps, urlDepsOpt) = depsExtraRepoOpt
      t <- AppArtifacts.dependencies(
        cache,
        params.repositories.repositories,
        params.dependency.platformOpt,
        params.output.verbosity,
        javaOrScalaDeps
      ).left.map(err => new Exception(err))
      (scalaVersion, _, deps) = t
      extraRepoOpt = urlDepsOpt.map { m =>
        val m0 = m.map {
          case ((mod, v), url) =>
            ((mod.module(scalaVersion), v), (url, true))
        }
        InMemoryRepository(
          m0,
          params.cache.cacheLocalArtifacts
        )
      }
      deps0 = Dependencies.addExclusions(
        deps,
        params.dependency.exclude.map { m =>
          val m0 = m.module(scalaVersion)
          (m0.organization, m0.name)
        },
        params.dependency.perModuleExclude.map {
          case (k, s) =>
            k.module(scalaVersion) -> s.map(_.module(scalaVersion))
        }
      )
      // Prepend FallbackDependenciesRepository to the repository list
      // so that dependencies with URIs are resolved against this repo
      repositories = extraRepoOpt.toSeq ++ params.repositories.repositories
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

      resAndWarnings <- coursier.Resolve()
        .withDependencies(deps0)
        .withRepositories(repositories)
        .withResolutionParams(params.resolution)
        .withCache(
          cache
        )
        .transformResolution { t =>
          if (params.benchmark == 0) t
          else benchmark(math.abs(params.benchmark))(t)
        }
        .transformFetcher { f =>
          if (params.output.verbosity >= 2) {
            modVers: Seq[(Module, String)] =>
              val print = Task.delay {
                Output.errPrintln(s"Getting ${modVers.length} project definition(s)")
              }

              print.flatMap(_ => f(modVers))
          } else
            f
        }
        .ioWithConflicts
        .attempt
        .flatMap {
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
            colors = !RefreshLogger.defaultFallbackMode
          )
        }
      }

      _ = {
        for (err <- errors)
          stderr.println(err.getMessage)
      }
    } yield res
  }

  // Add options and dependencies from the app to the passed options / dependencies
  def handleApps[T](
    options: T,
    args: Seq[String],
    channels: Seq[Module],
    repositories: Seq[Repository],
    cache: Cache[Task]
  )(
    withApp: (T, RawAppDescriptor) => T
  ): (T, Seq[String]) = {

    val (appIds, deps) = args.partition(s => s.count(_ == ':') <= 1)

    if (appIds.lengthCompare(1) > 0) {
      System.err.println(s"Error: only at most one app can be passed as dependency")
      sys.exit(1)
    }

    val descOpt = appIds.headOption.map { id =>

      val (actualId, overrideVersionOpt) = {
        val idx = id.indexOf(':')
        if (idx < 0)
          (id, None)
        else
          (id.take(idx), Some(id.drop(idx + 1)))
      }

      val e = for {
        b <- Install.appDescriptor(
          channels,
          repositories,
          cache,
          actualId
        ).right.map(_._2)
        desc <- RawAppDescriptor.parse(new String(b, StandardCharsets.UTF_8))
      } yield overrideVersionOpt.fold(desc)(desc.overrideVersion)

      e match {
        case Left(err) =>
          System.err.println(err)
          sys.exit(1)
        case Right(res) =>
          res.copy(
            name = res.name
              .orElse(Some(actualId)) // kind of meh - so that the id can be picked as default output name by bootstrap
          )
      }
    }

    descOpt.fold((options, args)) { desc =>
      // add desc.dependencies in deps at the former app id position? (to retain the order)
      (withApp(options, desc), desc.dependencies ++ deps)
    }
  }

  def run(options: ResolveOptions, args: RemainingArgs): Unit = {

    // get options and dependencies from apps if any
    val (options0, deps) = ResolveParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
      val initialRepositories = initialParams.repositories.repositories
      val channels = initialParams.repositories.channels
      val pool = Sync.fixedThreadPool(initialParams.cache.parallel)
      val cache = initialParams.cache.cache(pool, initialParams.output.logger())
      val res = handleApps(options, args.all, channels, initialRepositories, cache)(_.addApp(_))
      pool.shutdown()
      res
    }

    val params = ResolveParams(options0).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          Output.errPrintln(err)
        sys.exit(1)
      case Right(params0) =>
        params0
    }

    val pool = Sync.fixedThreadPool(params.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)

    val t = task(params, pool, System.out, System.err, deps)

    t.attempt.unsafeRun()(ec) match {
      case Left(e: ResolveException) if params.output.verbosity <= 1 =>
        Output.errPrintln(e.message)
        sys.exit(1)
      case Left(e) => throw e
      case Right(_) =>
    }
  }

}
