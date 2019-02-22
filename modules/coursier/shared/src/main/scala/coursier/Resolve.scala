package coursier

import coursier.cache.{Cache, CacheLogger}
import coursier.error.ResolutionError
import coursier.error.ResolutionError.UnsatisfiableRule
import coursier.error.conflict.UnsatisfiedRule
import coursier.extra.Typelevel
import coursier.params.ResolutionParams
import coursier.params.rule.{Rule, RuleResolution}
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

    val mapDependencies = {
      val l = (if (params.typelevel) Seq(Typelevel.swap) else Nil) ++
        (if (params.doForceScalaVersion) Seq(coursier.core.Resolution.forceScalaVersion(params.selectedScalaVersion)) else Nil)

      l.reduceOption((f, g) => dep => f(g(dep)))
    }

    Resolution(
      dependencies,
      forceVersions = params.forceVersion ++ forceScalaVersions,
      filter = Some(dep => params.keepOptionalDependencies || !dep.optional),
      userActivations =
        if (params.profiles.isEmpty) None
        else Some(params.profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
      forceProperties = params.forcedProperties,
      mapDependencies = mapDependencies
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

  def resolveIOWithConflicts[F[_]](
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[F] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => (),
    through: F[Resolution] => F[Resolution] = null, // running into weird inference issues at call site when using identity here
    transformFetcher: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F] = null
  )(implicit S: Schedulable[F]): F[(Resolution, Seq[UnsatisfiedRule])] = {

    val initialRes = initialResolution(dependencies, params)
    val fetch = {
      val f = fetchVia[F](repositories, cache)
      if (transformFetcher == null)
        f
      else
        transformFetcher(f)
    }

    def run(res: Resolution): F[Resolution] = {
      val t = runProcess(res, fetch, params.maxIterations, cache.loggerOpt, beforeLogging, afterLogging)
      if (through == null)
        t
      else
        through(t)
    }

    def validate0(res: Resolution): F[Resolution] =
      validate(res).either match {
        case Left(errors) =>
          val err = ResolutionError.from(errors.head, errors.tail: _*)
          S.fromAttempt(Left(err))
        case Right(()) =>
          S.point(res)
      }

    def recurseOnRules(res: Resolution, rules: Seq[(Rule, RuleResolution)]): F[(Resolution, List[UnsatisfiedRule])] =
      rules match {
        case Seq() =>
          S.point((res, Nil))
        case Seq((rule, ruleRes), t @ _*) =>
          rule.enforce(res, ruleRes) match {
            case Left(c) =>
              S.fromAttempt(Left(c))
            case Right(Left(c)) =>
              S.map(recurseOnRules(res, t)) {
                case (res0, conflicts) =>
                  (res0, c :: conflicts)
              }
            case Right(Right(None)) =>
              recurseOnRules(res, t)
            case Right(Right(Some(newRes))) =>
              S.bind(S.bind(run(newRes.copy(dependencies = Set.empty)))(validate0)) { res0 =>
                // FIXME check that the rule passes after it tried to address itself
                recurseOnRules(res0, t)
              }
          }
      }

    def validateAllRules(res: Resolution, rules: Seq[(Rule, RuleResolution)]): F[Resolution] =
      rules match {
        case Seq() =>
          S.point(res)
        case Seq((rule, _), t @ _*) =>
          rule.check(res) match {
            case Some(c) =>
              S.fromAttempt(Left(c))
            case None =>
              validateAllRules(res, t)
          }
      }

    S.bind(S.bind(run(initialRes))(validate0)) { res0 =>
      S.bind(recurseOnRules(res0, params.rules)) {
        case (res0, conflicts) =>
          S.map(validateAllRules(res0, params.rules)) { _ =>
            (res0, conflicts)
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
    afterLogging: Boolean => Unit = _ => (),
    through: F[Resolution] => F[Resolution] = null, // running into weird inference issues at call site when using identity here
    transformFetcher: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F] = null
  )(implicit S: Schedulable[F]): F[Resolution] = {
    val s = resolveIOWithConflicts(
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging,
      through,
      transformFetcher
    )
    S.map(s)(_._1)
  }

  def resolveFuture(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[Task] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => (),
    through: Task[Resolution] => Task[Resolution] = identity
  )(implicit ec: ExecutionContext = cache.ec): Future[Resolution] = {

    val task = resolveIO[Task](
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging,
      through
    )

    task.future()
  }

  def resolveEither(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository] = defaultRepositories,
    params: ResolutionParams = ResolutionParams(),
    cache: Cache[Task] = Cache.default,
    beforeLogging: () => Unit = () => (),
    afterLogging: Boolean => Unit = _ => (),
    through: Task[Resolution] => Task[Resolution] = identity
  )(implicit ec: ExecutionContext = cache.ec): Either[ResolutionError, Resolution] = {

    val task = resolveIO[Task](
      dependencies,
      repositories,
      params,
      cache,
      beforeLogging,
      afterLogging,
      through
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
        ValidationNel.failure(new ResolutionError.MaximumIterationReached(res))

    val checkErrors: ValidationNel[ResolutionError, Unit] = res
      .errors
      .map {
        case ((module, version), errors) =>
          new ResolutionError.CantDownloadModule(res, module, version, errors)
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
            res,
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
