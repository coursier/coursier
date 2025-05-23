package coursier.cli.resolve

import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.{Executors, ExecutorService, ThreadFactory}

import caseapp._
import coursier.cache.Cache
import coursier.cache.loggers.RefreshLogger
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.install.Install
import coursier.core.{Dependency, Module, Repository, Resolution}
import coursier.error.ResolutionError
import coursier.install.{AppArtifacts, AppDescriptor, Channel, Channels, RawAppDescriptor}
import coursier.parse.JavaOrScalaModule
import coursier.util._
import coursier.version.{Version, VersionConstraint, VersionInterval}

import scala.concurrent.ExecutionContext
import scala.util.Try

object Resolve extends CoursierCommand[ResolveOptions] {

  /** Tries to parse get dependencies via Scala Index lookups.
    */
  private def benchmark[T](iterations: Int): Task[T] => Task[T] = { run =>

    val res = for {
      start <- Task.delay(System.currentTimeMillis())
      res0  <- run
      end   <- Task.delay(System.currentTimeMillis())
      _ = Console.err.println(s"${end - start} ms")
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

  private def lift[A](f: => A) =
    Try(f).toEither

  private def unlift[A](e: => Either[Throwable, A]): A =
    e.fold(throw _, identity)

  private[cli] def depsAndReposOrError(
    params: SharedResolveParams,
    args: Seq[String],
    cache: Cache[Task]
  ) =
    lift {

      val fromFilesDependencies = params.dependency.fromFilesDependencies
      val (javaOrScalaDeps, urlDeps) = unlift {
        Dependencies.withExtraRepo(
          args ++ fromFilesDependencies,
          params.dependency.intransitiveDependencies
        )
      }

      val (sbtPluginJavaOrScalaDeps, sbtPluginUrlDeps) = unlift {
        Dependencies.withExtraRepo(
          Nil,
          params.dependency.sbtPluginDependencies
        )
      }

      val (scalaVersionOpt, platformOpt, deps) = unlift {
        AppDescriptor()
          .withDependencies(javaOrScalaDeps)
          .withRepositories(params.repositories.repositories)
          .withScalaVersionOpt(
            params.resolution.scalaVersionOpt0.map(_.asString).map { s =>
              // add a "+" to partial Scala version numbers such as "2.13", "2.12", "3"
              if (s.count(_ == '.') < 2 && s.forall(c => c.isDigit || c == '.')) s + "+"
              else s
            }
          )
          .processDependencies(
            cache,
            params.dependency.platformOpt,
            params.output.verbosity
          )
      }
      val scalaVersion = scalaVersionOpt
        .getOrElse {
          // we should only have Java dependencies in that case
          VersionConstraint.empty
        }

      val extraRepoOpt = Some(urlDeps ++ sbtPluginUrlDeps).filter(_.nonEmpty).map { m =>
        val m0 = m.map {
          case ((mod, version), url) =>
            ((mod.module(scalaVersion.asString), version), (url, true))
        }
        InMemoryRepository.privateApply(
          m0,
          params.cache.cacheLocalArtifacts
        )
      }

      val deps0 = Dependencies.addExclusions(
        deps ++ sbtPluginJavaOrScalaDeps.map(_.dependency(
          JavaOrScalaModule.scalaBinaryVersion(scalaVersion.asString),
          scalaVersion.asString,
          platformOpt.getOrElse("")
        )),
        params.dependency.perModuleExclude.map {
          case (k, s) =>
            k.module(scalaVersion.asString) -> s.map(_.module(scalaVersion.asString))
        }
      )

      // Prepend FallbackDependenciesRepository to the repository list
      // so that dependencies with URIs are resolved against this repo
      val repositories = extraRepoOpt.toSeq ++ params.repositories.repositories

      unlift {
        val invalidForced = extraRepoOpt
          .map(_.fallbacks0.toSeq)
          .getOrElse(Nil)
          .collect {
            case ((mod, version), _)
                if params.resolution.forceVersion0
                  .get(mod)
                  .exists(_ != VersionConstraint.fromVersion(version)) =>
              (mod, version)
          }
        if (invalidForced.isEmpty)
          Right(())
        else
          Left(
            new ResolveException(
              s"Cannot force a version that is different from the one specified for modules " +
                invalidForced
                  .map {
                    case (mod, ver) =>
                      s"${mod.repr}:${ver.asString}"
                  }
                  .mkString(", ") +
                " with url"
            )
          )
      }

      (deps0, repositories, scalaVersionOpt, platformOpt)
    }

  def printTask(
    params: ResolveParams,
    pool: ExecutorService,
    // stdout / stderr not used everywhere (added mostly for testing)
    stdout: PrintStream,
    stderr: PrintStream,
    args: Seq[String],
    benchmark: Int = 0,
    benchmarkCache: Boolean = false
  ): Task[Unit] = {
    val task0 = task(
      params.shared,
      pool,
      stdout,
      stderr,
      args,
      params.forcePrint,
      benchmark,
      benchmarkCache
    )

    val finalTask =
      params.retry match {
        case None                        => task0
        case Some((period, maxAttempts)) =>
          // meh
          val scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory {
              val defaultThreadFactory = Executors.defaultThreadFactory()
              def newThread(r: Runnable) = {
                val t = defaultThreadFactory.newThread(r)
                t.setDaemon(true)
                t.setName("retry-handler")
                t
              }
            }
          )
          val delay = Task.completeAfter(scheduler, period)
          def helper(
            count: Int
          ): Task[(Resolution, Option[String], Option[String], Option[ResolutionError])] =
            task0.attempt.flatMap {
              case Left(e) =>
                if (count >= maxAttempts) {
                  val ex = e match {
                    case _: ResolveException => new ResolveException(
                        s"Resolution still failing after $maxAttempts attempts: ${e.getMessage}",
                        e
                      )
                    case _ =>
                      new Exception(s"Resolution still failing after $maxAttempts attempts", e)
                  }
                  Task.fail(ex)
                }
                else {
                  val print = Task.delay {
                    // TODO Better printing of error (getMessage for relevent types, …)
                    stderr.println(s"Attempt $count failed: $e")
                  }
                  for {
                    _   <- print
                    _   <- delay
                    res <- helper(count + 1)
                  } yield res
                }
              case Right(res) => Task.point(res)
            }
          helper(1)
      }

    finalTask.flatMap {
      case (res, scalaVersionOpt, platformOpt, errorOpt) =>
        val outputToStdout = errorOpt.isEmpty || params.forcePrint
        if (outputToStdout || params.output.verbosity >= 2)
          Task.delay {
            Output.printResolutionResult(
              printResultStdout = outputToStdout,
              params,
              scalaVersionOpt,
              platformOpt,
              res,
              stdout,
              stderr,
              colors = coursier.paths.Util.useColorOutput()
            )
          }
        else
          Task.point(())
    }
  }

  // Roughly runs two kinds of side effects under the hood: printing output and asking things to the cache
  def task(
    params: SharedResolveParams,
    pool: ExecutorService,
    // stdout / stderr not used everywhere (added mostly for testing)
    stdout: PrintStream,
    stderr: PrintStream,
    args: Seq[String],
    force: Boolean = false,
    benchmark: Int = 0,
    benchmarkCache: Boolean = false
  ): Task[(Resolution, Option[String], Option[String], Option[ResolutionError])] = {

    val cache = params.cache.cache(
      pool,
      params.output.logger(),
      inMemoryCache = benchmark != 0 && benchmarkCache
    )

    val depsAndReposOrError0 = depsAndReposOrError(params, args, cache)

    for {

      res0 <- Task.fromEither(depsAndReposOrError0)
      (deps, repositories, scalaVersionOpt, platformOpt) = res0
      params0 = params.copy(
        resolution = params.updatedResolution(scalaVersionOpt)
      )

      _ = Output.printDependencies(params0.output, params0.resolution, deps, stdout, stderr)

      res1 <-
        coursier.Resolve()
          .withDependencies(deps)
          .withRepositories(repositories)
          .withResolutionParams(params0.resolution)
          .withBomDependencies(params0.dependency.bomDependencies)
          .withCache(cache)
          .transformResolution { t =>
            if (benchmark == 0) t
            else Resolve.benchmark(math.abs(benchmark))(t)
          }
          .transformFetcher { f =>
            if (params0.output.verbosity >= 2) {
              modVers =>
                val print = Task.delay {
                  Output.errPrintln(s"Getting ${modVers.length} project definition(s)")
                }

                print.flatMap(_ => f(modVers))
            }
            else
              f
          }
          .ioWithConflicts
          .attempt
          .flatMap {
            case Left(ex: ResolutionError) =>
              if (force)
                Task.point((ex.resolution, Nil, Some(ex)))
              else
                Task.fail(new ResolveException("Resolution error: " + ex.getMessage, ex))
            case e =>
              Task.fromEither(e.map { case (r, w) => (r, w, None) })
          }

      (res, _, errorOpt) = res1

      // TODO Print warnings

      _ = {
        for (ex <- errorOpt; err <- ex.errors)
          stderr.println(err.getMessage)
      }

    } yield (res, scalaVersionOpt.map(_.asString), platformOpt, errorOpt)

  }

  // Add options and dependencies from the app to the passed options / dependencies
  def handleApps[T](
    options: T,
    args: Seq[String],
    channels: Channels
  )(
    withApp: (T, RawAppDescriptor) => T
  ): (T, Seq[String]) = {

    val (inlineAppIds, args0) =
      args.partition(s => s.startsWith("{") || s.dropWhile(_ != ':').startsWith(":{"))
    val (appIds, deps) = args0.partition(s => s.count(_ == ':') <= 1)

    if (inlineAppIds.length + appIds.length > 1) {
      System.err.println(s"Error: only at most one app can be passed as dependency")
      sys.exit(1)
    }

    val inlineDescOpt = inlineAppIds.headOption.map { input =>

      val (nameOpt, json) = {
        val input0 = input.trim
        val idx    = input0.indexOf(":{")
        if (idx >= 0)
          (Some(input.take(idx)).filter(_.nonEmpty), input.drop(idx + 1))
        else
          (None, input)
      }

      val e =
        for {
          rawDesc <- RawAppDescriptor.parse(json)
        } yield rawDesc
          // kind of meh - so that the id can be picked as default output name by bootstrap
          // we have to update those ourselves, as these aren't put in the app descriptor bytes of AppInfo
          .withName(rawDesc.name.orElse(nameOpt))

      e match {
        case Left(err) =>
          System.err.println(err)
          sys.exit(1)
        case Right(res) => res
      }
    }

    def descOpt = appIds.headOption.map { id =>

      val e =
        for {
          info <- channels.appDescriptor(id)
            .attempt
            .flatMap {
              case Left(e: Channels.ChannelsException) => Task.point(Left(e.getMessage))
              case Left(e)                             => Task.fail(new Exception(e))
              case Right(res)                          => Task.point(Right(res))
            }
            .unsafeRun(wrapExceptions = true)(channels.cache.ec)
          rawDesc <- RawAppDescriptor.parse(
            new String(info.appDescriptorBytes, StandardCharsets.UTF_8)
          )
        } yield rawDesc
          // kind of meh - so that the id can be picked as default output name by bootstrap
          // we have to update those ourselves, as these aren't put in the app descriptor bytes of AppInfo
          .withName(rawDesc.name.orElse(info.appDescriptor.nameOpt))
          .overrideVersion(info.overrideVersionOpt, useVersionOverrides = true)

      e match {
        case Left(err) =>
          System.err.println(err)
          sys.exit(1)
        case Right(res) => res
      }
    }

    inlineDescOpt.orElse(descOpt).fold((options, args)) { desc =>
      // add desc.dependencies in deps at the former app id position? (to retain the order)
      (withApp(options, desc), desc.dependencies ++ deps)
    }
  }

  override def group: String = CommandGroup.resolve

  def run(options: ResolveOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) =
      ResolveParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
        val initialRepositories = initialParams.repositories.repositories
        val channels            = initialParams.channel.channels
        pool = Sync.fixedThreadPool(initialParams.cache.parallel)
        val cache     = initialParams.cache.cache(pool, initialParams.output.logger())
        val channels0 = Channels(channels, initialRepositories, cache)
        val res       = handleApps(options, args.all, channels0)(_.addApp(_))
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

    val t = printTask(
      params,
      pool,
      System.out,
      System.err,
      deps,
      benchmark = params.benchmark,
      benchmarkCache = params.benchmarkCache
    )

    t.attempt.unsafeRun(wrapExceptions = true)(ec) match {
      case Left(e: ResolveException) if params.output.verbosity <= 1 =>
        Output.errPrintln(e.getMessage)
        sys.exit(1)
      case Left(e: AppArtifacts.AppArtifactsException) if params.output.verbosity <= 1 =>
        Output.errPrintln(e.getMessage)
        sys.exit(1)
      case Left(e)   => throw new Exception(e)
      case Right(()) =>
    }
  }

}
