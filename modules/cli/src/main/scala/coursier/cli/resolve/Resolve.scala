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
import coursier.cli.install.Install
import coursier.cli.scaladex.Scaladex
import coursier.cli.util.MonadlessTask._
import coursier.core.{Dependency, Module, Repository}
import coursier.error.ResolutionError
import coursier.install.{AppArtifacts, AppDescriptor, Channel, Channels, RawAppDescriptor}
import coursier.parse.JavaOrScalaModule
import coursier.util._

import scala.concurrent.ExecutionContext

object Resolve extends CaseApp[ResolveOptions] {

  /**
    * Tries to parse get dependencies via Scala Index lookups.
    */
  def handleScaladexDependencies(
    params: ResolveParams,
    pool: ExecutorService,
    scalaVersion: String
  ): Task[List[Dependency]] =
    if (params.dependency.scaladexLookups.isEmpty)
      Task.point(Nil)
    else {

      val logger = params.output.logger()

      val scaladex = Scaladex.withCache(params.cache.cache(pool, logger).fetch)

      val tasks = params.dependency.scaladexLookups.map { s =>
        Dependencies.handleScaladexDependency(s, scalaVersion, scaladex, params.output.verbosity)
          .map {
            case Left(error) => Validated.invalidNel(error)
            case Right(l) => Validated.validNel(l)
          }
      }

      lift {
        logger.init()

        try {
          val l = unlift(Gather[Task].gather(tasks))

          l.toList.flatSequence.toEither match {
            case Right(l0) => l0
            case Left(errs) =>
              unlift {
                Task.fail(
                  new ResolveException(
                    s"Error during Scaladex lookups:\n" +
                      errs.toList.map("  " + _).mkString("\n")
                  )
                )
              }
          }
        }
        finally {
          logger.stop()
        }
      }
    }

  private def benchmark[T](iterations: Int): Task[T] => Task[T] = { run =>

    val res = lift {

      val start = unlift(Task.delay(System.currentTimeMillis()))
      val res0 = unlift(run)
      val end = unlift(Task.delay(System.currentTimeMillis()))
      Console.err.println(s"${end - start} ms")

      res0
    }

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


  private[cli] def depsAndReposOrError(
    params: ResolveParams,
    args: Seq[String],
    cache: Cache[Task]
  ) = {
    import coursier.cli.util.MonadlessEitherThrowable._

    lift {

      val (javaOrScalaDeps, urlDeps) = unlift {
        Dependencies.withExtraRepo(
          args,
          params.dependency.intransitiveDependencies
        )
      }

      val (sbtPluginJavaOrScalaDeps, sbtPluginUrlDeps) = unlift {
        Dependencies.withExtraRepo(
          Nil,
          params.dependency.sbtPluginDependencies
        )
      }

      val (scalaVersion, platformOpt, deps) = unlift {
        AppDescriptor()
          .withDependencies(javaOrScalaDeps)
          .withRepositories(params.repositories.repositories)
          .withScalaVersionOpt(
            params.resolution.scalaVersionOpt.map { s =>
              if (s.count(_ == '.') == 1 && s.forall(c => c.isDigit || c == '.')) s + "+"
              else s
            }
          )
          .processDependencies(
            cache,
            params.dependency.platformOpt,
            params.output.verbosity
          )
          .left.map(err => new Exception(err))
      }

      val extraRepoOpt = Some(urlDeps ++ sbtPluginUrlDeps).filter(_.nonEmpty).map { m =>
        val m0 = m.map {
          case ((mod, v), url) =>
            ((mod.module(scalaVersion), v), (url, true))
        }
        InMemoryRepository(
          m0,
          params.cache.cacheLocalArtifacts
        )
      }

      val deps0 = Dependencies.addExclusions(
        deps ++ sbtPluginJavaOrScalaDeps.map(_.dependency(JavaOrScalaModule.scalaBinaryVersion(scalaVersion), scalaVersion, platformOpt.getOrElse(""))),
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
      val repositories = extraRepoOpt.toSeq ++ params.repositories.repositories

      unlift {
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

      (deps0, repositories, scalaVersion, platformOpt)
    }
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
  ): Task[(Resolution, String, Option[String])] = {

    val cache = params.cache.cache(
      pool,
      params.output.logger(),
      inMemoryCache = params.benchmark != 0 && params.benchmarkCache
    )

    val depsAndReposOrError0 = depsAndReposOrError(params, args, cache)

    lift {

      val (deps, repositories, scalaVersion, platformOpt) = unlift(Task.fromEither(depsAndReposOrError0))
      val params0 = params.copy(
        resolution = params.resolution
          .withScalaVersionOpt(params.resolution.scalaVersionOpt.map(_ => scalaVersion))
      )

      val scaladexDeps = unlift(handleScaladexDependencies(params0, pool, scalaVersion))

      val deps0 = deps ++ scaladexDeps

      Output.printDependencies(params0.output, params0.resolution, deps0, stdout, stderr)

      val (res, _, errors) = unlift {
        coursier.Resolve()
          .withDependencies(deps0)
          .withRepositories(repositories)
          .withResolutionParams(params0.resolution)
          .withCache(cache)
          .transformResolution { t =>
            if (params0.benchmark == 0) t
            else benchmark(math.abs(params0.benchmark))(t)
          }
          .transformFetcher { f =>
            if (params0.output.verbosity >= 2) {
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
              if (force || params0.output.forcePrint)
                Task.point((ex.resolution, Nil, ex.errors))
              else
                Task.fail(new ResolveException("Resolution error: " + ex.getMessage, ex))
            case e =>
              Task.fromEither(e.map { case (r, w) => (r, w, Nil) })
          }
      }

      // TODO Print warnings

      val valid = errors.isEmpty

      val outputToStdout = printOutput && (valid || params0.output.forcePrint)
      if (outputToStdout || params0.output.verbosity >= 2) {
        Output.printResolutionResult(
          printResultStdout = outputToStdout,
          params0,
          scalaVersion,
          platformOpt,
          res,
          stdout,
          stderr,
          colors = !RefreshLogger.defaultFallbackMode
        )
      }

      for (err <- errors)
        stderr.println(err.getMessage)

      (res, scalaVersion, platformOpt)
    }
  }

  // Add options and dependencies from the app to the passed options / dependencies
  def handleApps[T](
    options: T,
    args: Seq[String],
    channels: Channels
  )(
    withApp: (T, RawAppDescriptor) => T
  ): (T, Seq[String]) = {

    val (appIds, deps) = args.partition(s => s.count(_ == ':') <= 1)

    if (appIds.lengthCompare(1) > 0) {
      System.err.println(s"Error: only at most one app can be passed as dependency")
      sys.exit(1)
    }

    val descOpt = appIds.headOption.map { id =>

      val e = for {
        info <- channels.appDescriptor(id)
          .attempt
          .flatMap {
            case Left(e: Channels.ChannelsException) => Task.point(Left(e.getMessage))
            case Left(e) => Task.fail(e)
            case Right(res) => Task.point(Right(res))
          }
          .unsafeRun()(channels.cache.ec)
        rawDesc <- RawAppDescriptor.parse(new String(info.appDescriptorBytes, StandardCharsets.UTF_8))
      } yield {
        rawDesc
          // kind of meh - so that the id can be picked as default output name by bootstrap
          // we have to update those ourselves, as these aren't put in the app descriptor bytes of AppInfo
          .withName(rawDesc.name.orElse(info.appDescriptor.nameOpt))
          .overrideVersion(info.overrideVersionOpt)
      }

      e match {
        case Left(err) =>
          System.err.println(err)
          sys.exit(1)
        case Right(res) => res
      }
    }

    descOpt.fold((options, args)) { desc =>
      // add desc.dependencies in deps at the former app id position? (to retain the order)
      (withApp(options, desc), desc.dependencies ++ deps)
    }
  }

  def run(options: ResolveOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) = ResolveParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
      val initialRepositories = initialParams.repositories.repositories
      val channels = initialParams.repositories.channels
      pool = Sync.fixedThreadPool(initialParams.cache.parallel)
      val cache = initialParams.cache.cache(pool, initialParams.output.logger())
      val channels0 = Channels(channels, initialRepositories, cache)
      val res = handleApps(options, args.all, channels0)(_.addApp(_))
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

    if (pool == null)
      pool = Sync.fixedThreadPool(params.cache.parallel)
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
