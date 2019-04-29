package coursier

import coursier.cache.{Cache, CacheLogger}
import coursier.error.ResolutionError
import coursier.error.conflict.UnsatisfiedRule
import coursier.internal.Typelevel
import coursier.params.{Mirror, MirrorConfFile, ResolutionParams}
import coursier.params.rule.{Rule, RuleResolution}
import coursier.util._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

final class Resolve[F[_]] private[coursier] (private val params: Resolve.Params[F]) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Resolve[_] =>
        params == other.params
    }

  override def hashCode(): Int =
    17 + params.##

  override def toString: String =
    s"Resolve($params)"


  def dependencies: Seq[Dependency] =
    params.dependencies
  def repositories: Seq[Repository] =
    params.repositories
  def mirrors: Seq[Mirror] =
    params.mirrors
  def mirrorConfFiles: Seq[MirrorConfFile] =
    params.mirrorConfFiles
  def resolutionParams: ResolutionParams =
    params.resolutionParams
  def cache: Cache[F] =
    params.cache
  def throughOpt: Option[F[Resolution] => F[Resolution]] =
    params.throughOpt
  def transformFetcherOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]] =
    params.transformFetcherOpt
  def S: Sync[F] =
    params.S

  def finalRepositories: F[Seq[Repository]] =
    S.map(allMirrors) { mirrors0 =>
      repositories
        .map { repo =>
          val it = mirrors0
            .iterator
            .flatMap(_.matches(repo).iterator)
          if (it.hasNext)
            it.next()
          else
            repo
        }
        .distinct
    }

  private def withParams(params: Resolve.Params[F]): Resolve[F] =
    new Resolve(params)


  def withDependencies(dependencies: Seq[Dependency]): Resolve[F] =
    withParams(params.copy(dependencies = dependencies))
  def addDependencies(dependencies: Dependency*): Resolve[F] =
    withParams(params.copy(dependencies = params.dependencies ++ dependencies))

  def withRepositories(repositories: Seq[Repository]): Resolve[F] =
    withParams(params.copy(repositories = repositories))
  def addRepositories(repositories: Repository*): Resolve[F] =
    withParams(params.copy(repositories = params.repositories ++ repositories))

  def noMirrors: Resolve[F] =
    withParams(params.copy(
      mirrors = Nil,
      mirrorConfFiles = Nil
    ))

  def withMirrors(mirrors: Seq[Mirror]): Resolve[F] =
    withParams(params.copy(mirrors = mirrors))
  def addMirrors(mirrors: Mirror*): Resolve[F] =
    withParams(params.copy(mirrors = params.mirrors ++ mirrors))

  def withMirrorConfFiles(mirrorConfFiles: Seq[MirrorConfFile]): Resolve[F] =
    withParams(params.copy(mirrorConfFiles = mirrorConfFiles))
  def addMirrorConfFiles(mirrorConfFiles: MirrorConfFile*): Resolve[F] =
    withParams(params.copy(mirrorConfFiles = params.mirrorConfFiles ++ mirrorConfFiles))

  def withResolutionParams(resolutionParams: ResolutionParams): Resolve[F] =
    withParams(params.copy(resolutionParams = resolutionParams))

  def withCache(cache: Cache[F]): Resolve[F] =
    withParams(params.copy(cache = cache))

  def transformResolution(f: F[Resolution] => F[Resolution]): Resolve[F] =
    withParams(params.copy(throughOpt = Some(params.throughOpt.fold(f)(_ andThen f))))
  def noTransformResolution(): Resolve[F] =
    withParams(params.copy(throughOpt = None))
  def withTransformResolution(fOpt: Option[F[Resolution] => F[Resolution]]): Resolve[F] =
    withParams(params.copy(throughOpt = fOpt))

  def transformFetcher(f: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]): Resolve[F] =
    withParams(params.copy(transformFetcherOpt = Some(params.transformFetcherOpt.fold(f)(_ andThen f))))
  def noTransformFetcher(): Resolve[F] =
    withParams(params.copy(transformFetcherOpt = None))
  def withTransformFetcher(fOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]]): Resolve[F] =
    withParams(params.copy(transformFetcherOpt = fOpt))

  private def allMirrors0 =
    mirrors ++ mirrorConfFiles.flatMap(_.mirrors())

  def allMirrors: F[Seq[Mirror]] =
    S.delay(allMirrors0)


  private def fetchVia: F[ResolutionProcess.Fetch[F]] = {
    val fetchs = params.cache.fetchs
    S.map(finalRepositories)(r => ResolutionProcess.fetch(r, fetchs.head, fetchs.tail: _*)(S))
  }

  private def ioWithConflicts0(fetch: ResolutionProcess.Fetch[F]): F[(Resolution, Seq[UnsatisfiedRule])] = {

    val initialRes = Resolve.initialResolution(params.dependencies, params.resolutionParams)

    def run(res: Resolution): F[Resolution] = {
      val t = Resolve.runProcess(res, fetch, params.resolutionParams.maxIterations, params.cache.loggerOpt)(S)
      params.through(t)
    }

    def validate0(res: Resolution): F[Resolution] =
      Resolve.validate(res).either match {
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
      S.bind(recurseOnRules(res0, params.resolutionParams.rules)) {
        case (res0, conflicts) =>
          S.map(validateAllRules(res0, params.resolutionParams.rules)) { _ =>
            (res0, conflicts)
          }
      }
    }
  }

  def ioWithConflicts: F[(Resolution, Seq[UnsatisfiedRule])] =
    S.bind(fetchVia) { f =>
      val fetchVia0 = params.transformFetcher(f)
      ioWithConflicts0(fetchVia0)
    }

  def io: F[Resolution] =
    S.map(ioWithConflicts)(_._1)

}

object Resolve extends PlatformResolve {

  private[coursier] def defaultParams[F[_]](cache: Cache[F])(implicit S: Sync[F]) =
    Params(
      Nil,
      defaultRepositories,
      defaultMirrorConfFiles,
      Nil,
      ResolutionParams(),
      cache,
      None,
      None,
      S
    )

  // Ideally, cache shouldn't be passed here, and a default one should be created from S.
  // But that would require changes in Sync or an extra typeclass (similar to Async in cats-effect)
  // to allow to use the default cache on Scala.JS with a generic F.
  def apply[F[_]](cache: Cache[F] = Cache.default)(implicit S: Sync[F]): Resolve[F] =
    new Resolve(defaultParams(cache))

  implicit class ResolveTaskOps(private val resolve: Resolve[Task]) extends AnyVal {

    def future()(implicit ec: ExecutionContext = resolve.params.cache.ec): Future[Resolution] =
      resolve.io.future()

    def either()(implicit ec: ExecutionContext = resolve.params.cache.ec): Either[ResolutionError, Resolution] = {

      val f = resolve
        .io
        .map(Right(_))
        .handle { case ex: ResolutionError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = resolve.params.cache.ec): Resolution = {
      val f = future()(ec)
      Await.result(f, Duration.Inf)
    }

  }

  private[coursier] final case class Params[F[_]](
    dependencies: Seq[Dependency],
    repositories: Seq[Repository],
    mirrorConfFiles: Seq[MirrorConfFile],
    mirrors: Seq[Mirror],
    resolutionParams: ResolutionParams,
    cache: Cache[F],
    throughOpt: Option[F[Resolution] => F[Resolution]],
    transformFetcherOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]],
    S: Sync[F]
  ) {
    def through: F[Resolution] => F[Resolution] =
      throughOpt.getOrElse(identity[F[Resolution]])
    def transformFetcher: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F] =
      transformFetcherOpt.getOrElse(identity[ResolutionProcess.Fetch[F]])

    override def toString: String =
      productIterator.mkString("ResolveParams(", ", ", ")")
  }

  private[coursier] def initialResolution(
    dependencies: Seq[Dependency],
    params: ResolutionParams = ResolutionParams()
  ): Resolution = {

    val forceScalaVersions =
      if (params.doForceScalaVersion) {
        val scalaOrg =
          if (params.typelevel) Organization("org.typelevel")
          else Organization("org.scala-lang")
        Seq(
          Module(scalaOrg, ModuleName("scala-library")) -> params.selectedScalaVersion,
          Module(scalaOrg, ModuleName("org.scala-lang:scala-reflect")) -> params.selectedScalaVersion,
          Module(scalaOrg, ModuleName("org.scala-lang:scala-compiler")) -> params.selectedScalaVersion,
          Module(scalaOrg, ModuleName("org.scala-lang:scalap")) -> params.selectedScalaVersion
        )
      } else
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
      extraProperties = params.properties,
      forceProperties = params.forcedProperties,
      mapDependencies = mapDependencies
    )
  }

  private[coursier] def runProcess[F[_]](
    initialResolution: Resolution,
    fetch: ResolutionProcess.Fetch[F],
    maxIterations: Int = 200,
    loggerOpt: Option[CacheLogger] = None
  )(implicit S: Sync[F]): F[Resolution] = {

    val task = initialResolution
      .process
      .run(fetch, maxIterations)

    loggerOpt match {
      case None =>
        task
      case Some(logger) =>
        S.bind(S.delay(logger.init())) { _ =>
          S.bind(S.attempt(task)) { a =>
            S.bind(S.delay(logger.stop())) { _ =>
              S.fromAttempt(a)
            }
          }
        }
    }
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
