package coursier

import coursier.cache.{Cache, CacheLogger}
import coursier.error.ResolutionError
import coursier.params.ResolutionParams
import coursier.util._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

object Resolve extends PlatformResolve {

  private[coursier] def initialResolution(
    dependencies: Seq[Dependency],
    params: ResolutionParams = ResolutionParams()
  ): Resolution = {
    val forceScalaVersions =
      if (params.doForceScalaVersion)
        Seq(
          mod"org.scala-lang:scala-library" -> params.selectedScalaVersion,
          mod"org.scala-lang:scala-reflect" -> params.selectedScalaVersion,
          mod"org.scala-lang:scala-compiler" -> params.selectedScalaVersion,
          mod"org.scala-lang:scalap" -> params.selectedScalaVersion
        )
      else
        Nil
    Resolution(
      dependencies,
      forceVersions = params.forceVersion ++ forceScalaVersions,
      filter = Some(dep => params.keepOptionalDependencies || !dep.optional),
      userActivations =
        if (params.profiles.isEmpty) None
        else Some(params.profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
      // FIXME Add that back? (Typelevel is in the extra module)
      // mapDependencies = if (params.typelevel) Some(Typelevel.swap) else None,
      forceProperties = params.forcedProperties
    )
  }

  private[coursier] def runProcess[F[_]](
    initialResolution: Resolution,
    fetch: ResolutionProcess.Fetch[F],
    maxIterations: Int = 200,
    loggerOpt: Option[CacheLogger] = None,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => ()
  )(implicit S: Schedulable[F]): F[Resolution] = {

    val task = initialResolution
      .process
      .run(fetch, maxIterations)

    loggerOpt match {
      case None =>
        task
      case Some(logger) =>
        S.bind(S.delay(logger.init(beforeLogging()))) { _ =>
          S.bind(S.attempt(task)) { a =>
            S.bind(S.delay { val b = logger.stop(); afterLogging(b) }) { _ =>
              S.fromAttempt(a)
            }
          }
        }
    }
  }

  def resolveIO[F[_]](
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[F] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => ()
  )(implicit S: Schedulable[F]): F[Resolution] = {
    val initialRes = initialResolution(dependencies, params)
    val fetch = fetchVia[F](repositories, cache)
    val res = runProcess(initialRes, fetch, params.maxIterations, cache.loggerOpt, beforeLogging, afterLogging)
    S.bind(res) { res0 =>
      validate(res0).either match {
        case Left(errors) =>
          val err = ResolutionError.from(errors.head, errors.tail: _*)
          S.fromAttempt(Left(err))
        case Right(()) =>
          S.point(res0)
      }
    }
  }

  def resolveFuture(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[Task] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => ()
  )(implicit ec: ExecutionContext = cache.ec): Future[Resolution] = {

    val task = resolveIO[Task](
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging
    )

    task.future()
  }

  def resolveEither(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[Task] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => ()
  )(implicit ec: ExecutionContext = cache.ec): Either[ResolutionError, Resolution] = {

    val task = resolveIO[Task](
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging
    )

    val f = task
      .map(Right(_))
      .handle { case ex: ResolutionError => Left(ex) }
      .future()

    Await.result(f, Duration.Inf)
  }

  def resolve(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[Task] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => ()
  )(implicit ec: ExecutionContext = cache.ec): Resolution = {

    val f = resolveFuture(
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging
    )(ec)

    Await.result(f, Duration.Inf)
  }

  private[coursier] def fetchVia[F[_]](
    repositories: Seq[Repository],
    cache: Cache[F] = Cache.default
  )(implicit S: Gather[F]): ResolutionProcess.Fetch[F] = {
    val fetchs = cache.fetchs
    ResolutionProcess.fetch(repositories, fetchs.head, fetchs.tail: _*)
  }

  def validate(res: Resolution): ValidationNel[ResolutionError, Unit] = {

    val checkDone: ValidationNel[ResolutionError, Unit] =
      if (res.isDone)
        ValidationNel.success(())
      else
        ValidationNel.failure(new ResolutionError.MaximumIterationReached)

    val checkErrors: ValidationNel[ResolutionError, Unit] = res
      .errors
      .map {
        case ((module, version), errors) =>
          new ResolutionError.CantDownloadModule(module, version, errors)
      } match {
        case Seq() =>
          ValidationNel.success(())
        case Seq(h, t @ _*) =>
          ValidationNel.failures(h, t: _*)
      }

    val checkConflicts: ValidationNel[ResolutionError, Unit] =
      if (res.conflicts.isEmpty)
        ValidationNel.success(())
      else
        ValidationNel.failure(
          new ResolutionError.ConflictingDependencies(
            res.conflicts.map { dep =>
              dep.copy(
                version = res.projectCache.get(dep.moduleVersion).fold(dep.version)(_._2.actualVersion)
              )
            }
          )
        )

    checkDone.zip(checkErrors, checkConflicts).map {
      case ((), (), ()) =>
    }
  }

}
