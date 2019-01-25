package coursier

import java.util.concurrent.ExecutorService

import coursier.cache.{CacheDefaults, CacheLogger}
import coursier.params.{CacheParams, ResolutionParams}
import coursier.util.{Print, Schedulable, Task, ValidationNel}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

object Resolve {

  def initialResolution(
    dependencies: Iterable[Dependency],
    params: ResolutionParams = ResolutionParams()
  ): Resolution =
    Resolution(
      dependencies.toSet,
      forceVersions = params.forceVersion,
      filter = Some(dep => params.keepOptionalDependencies || !dep.optional),
      userActivations =
        if (params.profiles.isEmpty) None
        else Some(params.profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
      // FIXME Add that back? (Typelevel is in the extra module)
      // mapDependencies = if (params.typelevel) Some(Typelevel.swap(_)) else None,
      forceProperties = params.forcedProperties
    )

  def runProcess[F[_]](
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

  def run[F[_]](
    initialResolution: Resolution,
    repositories: Seq[Repository],
    maxIterations: Int = 200,
    cacheParams: CacheParams = CacheParams(),
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F]): F[Resolution] = {
    val fetch = fetchVia[F](
      repositories,
      cacheParams,
      pool,
      logger
    )
    runProcess(initialResolution, fetch, maxIterations, logger)
  }


  def runFuture(
    initialResolution: Resolution,
    repositories: Seq[Repository],
    maxIterations: Int = 200,
    cacheParams: CacheParams = CacheParams(),
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  ): Future[Resolution] = {

    val task = run[Task](
      initialResolution,
      repositories,
      maxIterations,
      cacheParams,
      pool,
      logger
    )

    task.future()(ExecutionContext.fromExecutorService(pool))
  }

  def runSync(
    initialResolution: Resolution,
    repositories: Seq[Repository],
    logger: CacheLogger = CacheLogger.nop,
    maxIterations: Int = 200,
    cacheParams: CacheParams = CacheParams(),
    pool: ExecutorService = CacheDefaults.pool
  ): Resolution = {

    val f = runFuture(
      initialResolution,
      repositories,
      maxIterations,
      cacheParams,
      pool,
      logger
    )

    Await.result(f, Duration.Inf)
  }

  def fetcher[F[_]](
    cacheParams: CacheParams = CacheParams(),
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F]): Repository.Fetch[F] =
    Cache.fetch[F](
      cacheParams.cache,
      cacheParams.cachePolicies,
      checksums = cacheParams.checksum,
      logger = Some(logger),
      pool = pool,
      ttl = cacheParams.ttl,
      followHttpToHttpsRedirections = cacheParams.followHttpToHttpsRedirections
    )

  def fetchVia[F[_]](
    repositories: Seq[Repository],
    cacheParams: CacheParams = CacheParams(),
    pool: ExecutorService = CacheDefaults.pool,
    logger: CacheLogger = CacheLogger.nop
  )(implicit S: Schedulable[F]): ResolutionProcess.Fetch[F] = {
    val f = fetcher[F](cacheParams, pool, logger)
    ResolutionProcess.fetch(repositories, f)
  }

  def validate(res: Resolution, verbosity: Int): ValidationNel[String, Unit] = {

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
              printExclusions = verbosity >= 1
            )
        )

    checkDone.zip(checkErrors, checkConflicts).map {
      case ((), (), ()) =>
    }
  }

}
