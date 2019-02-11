package coursier

import coursier.cache.{CacheDefaults, CacheInterface, CacheLogger}
import coursier.params.ResolutionParams
import coursier.util.{Print, Schedulable, Task, ValidationNel}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

object Resolve {

  private[coursier] def initialResolution(
    dependencies: Seq[Dependency],
    params: ResolutionParams = ResolutionParams()
  ): Resolution =
    Resolution(
      dependencies,
      forceVersions = params.forceVersion,
      filter = Some(dep => params.keepOptionalDependencies || !dep.optional),
      userActivations =
        if (params.profiles.isEmpty) None
        else Some(params.profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
      // FIXME Add that back? (Typelevel is in the extra module)
      // mapDependencies = if (params.typelevel) Some(Typelevel.swap) else None,
      forceProperties = params.forcedProperties
    )

  private[coursier] def runProcess[F[_]](
    initialResolution: Resolution,
    fetch: ResolutionProcess.Fetch[F],
    maxIterations: Int = 200,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F]): F[Resolution] = {
    val task = initialResolution
      .process
      .run(fetch, maxIterations)

    S.bind(S.delay(logger.init(()))) { _ =>
      S.bind(S.attempt(task)) { a =>
        S.bind(S.delay(logger.stopDidPrintSomething())) { _ =>
          S.fromAttempt(a)
        }
      }
    }
  }

  def resolve[F[_]](
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = CacheDefaults.defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: CacheInterface[F] = Cache.default,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F]): F[Resolution] = {
    val initialRes = initialResolution(dependencies, params)
    val fetch = fetchVia[F](repositories, cache)
    runProcess(initialRes, fetch, params.maxIterations, logger)
  }

  def resolveFuture(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository],
    params: ResolutionParams = ResolutionParams(),
    cache: CacheInterface[Task] = Cache.default,
    logger: CacheLogger = CacheLogger.nop
  )(implicit ec: ExecutionContext = ExecutionContext.fromExecutorService(cache.pool)): Future[Resolution] = {

    val task = resolve[Task](
      dependencies,
      repositories,
      params,
      cache,
      logger
    )

    task.future()
  }

  def resolveSync(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository],
    params: ResolutionParams = ResolutionParams(),
    cache: CacheInterface[Task] = Cache.default,
    logger: CacheLogger = CacheLogger.nop
  )(implicit ec: ExecutionContext = ExecutionContext.fromExecutorService(cache.pool)): Resolution = {

    val f = resolveFuture(
      dependencies,
      repositories,
      params,
      cache,
      logger
    )(ec)

    Await.result(f, Duration.Inf)
  }

  private[coursier] def fetchVia[F[_]](
    repositories: Seq[Repository],
    cache: CacheInterface[F] = Cache.default
  )(implicit S: Schedulable[F]): ResolutionProcess.Fetch[F] = {
    val fetchs = cache.fetchs
    ResolutionProcess.fetch(repositories, fetchs.head, fetchs.tail: _*)
  }

  def validate(res: Resolution, exclusionsInErrors: Boolean): ValidationNel[String, Unit] = {

    val checkDone: ValidationNel[String, Unit] =
      if (res.isDone)
        ValidationNel.success(())
      else
        ValidationNel.failure("Maximum number of iterations reached!")

    val checkErrors: ValidationNel[String, Unit] = res
      .errors
      .map {
        case ((module, version), errors) =>
          s"$module:$version\n${errors.map("  " + _.replace("\n", "  \n")).mkString("\n")}"
      } match {
        case Seq() =>
          ValidationNel.success(())
        case Seq(h, t @ _*) =>
          ValidationNel.failures(h, t: _*)
      }

    val checkConflicts: ValidationNel[String, Unit] =
      if (res.conflicts.isEmpty)
        ValidationNel.success(())
      else
        ValidationNel.failure(
          s"\nConflict:\n" +
            Print.dependenciesUnknownConfigs(
              res.conflicts.toVector,
              res.projectCache.map { case (k, (_, p)) => k -> p },
              printExclusions = exclusionsInErrors
            )
        )

    checkDone.zip(checkErrors, checkConflicts).map {
      case ((), (), ()) =>
    }
  }

}
