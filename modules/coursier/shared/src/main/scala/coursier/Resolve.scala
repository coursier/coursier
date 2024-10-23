package coursier

import java.util.concurrent.ConcurrentHashMap

import coursier.cache.{Cache, CacheLogger}
import coursier.core.{
  Activation,
  Dependency,
  DependencySet,
  Exclusions,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Reconciliation,
  Repository,
  Resolution,
  ResolutionProcess
}
import coursier.error.ResolutionError
import coursier.error.conflict.UnsatisfiedRule
import coursier.graph.ReverseModuleTree
import coursier.internal.Typelevel
import coursier.params.{Mirror, MirrorConfFile, ResolutionParams}
import coursier.params.rule.{Rule, RuleResolution}
import coursier.util._
import coursier.util.Monad.ops._
import coursier.util.StringInterpolators._
import dataclass.{data, since}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

// format: off
@data class Resolve[F[_]](
  cache: Cache[F],
  dependencies: Seq[Dependency] = Nil,
  repositories: Seq[Repository] = Resolve.defaultRepositories,
  mirrorConfFiles: Seq[MirrorConfFile] = Resolve.defaultMirrorConfFiles,
  mirrors: Seq[Mirror] = Nil,
  resolutionParams: ResolutionParams = ResolutionParams(),
  throughOpt: Option[F[Resolution] => F[Resolution]] = None,
  transformFetcherOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]] = None,
  @since
    initialResolution: Option[Resolution] = None,
  @since
    confFiles: Seq[Resolve.Path] = Resolve.defaultConfFiles,
  preferConfFileDefaultRepositories: Boolean = true,
  @since("2.1.12")
  @deprecated("Workaround for former uses of Resolution.mapDependencies, prefer relying on ResolutionParams", "2.1.12")
    mapDependenciesOpt: Option[Dependency => Dependency] = None
)(implicit
  sync: Sync[F]
) {
  // format: on

  private def S = sync

  private def through: F[Resolution] => F[Resolution] =
    throughOpt.getOrElse(identity[F[Resolution]])
  private def transformFetcher: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F] =
    transformFetcherOpt.getOrElse(identity[ResolutionProcess.Fetch[F]])

  def finalDependencies: Seq[Dependency] = {

    val exclusions = MinimizedExclusions(resolutionParams.exclusions)

    dependencies
      .filter { dep =>
        exclusions(dep.module.organization, dep.module.name)
      }
      .map { dep =>
        dep.withMinimizedExclusions(
          dep.minimizedExclusions.join(exclusions)
        )
      }
  }

  def finalRepositories: F[Seq[Repository]] = {
    val repositories0 =
      if (preferConfFileDefaultRepositories) {
        val defaultFromConfOpt = confFiles
          .iterator
          .flatMap(Resolve.confFileRepositories(_).iterator)
          .take(1)
          .toList
          .headOption
        defaultFromConfOpt.getOrElse(repositories)
      }
      else
        repositories
    allMirrors.map(Mirror.replace(repositories0, _))
  }

  def addDependencies(dependencies: Dependency*): Resolve[F] =
    withDependencies(this.dependencies ++ dependencies)

  def addRepositories(repositories: Repository*): Resolve[F] =
    withRepositories(this.repositories ++ repositories)

  def noMirrors: Resolve[F] =
    withMirrors(Nil).withMirrorConfFiles(Nil)

  def addMirrors(mirrors: Mirror*): Resolve[F] =
    withMirrors(this.mirrors ++ mirrors)

  def addMirrorConfFiles(mirrorConfFiles: MirrorConfFile*): Resolve[F] =
    withMirrorConfFiles(this.mirrorConfFiles ++ mirrorConfFiles)
  def addConfFiles(confFiles: Resolve.Path*): Resolve[F] =
    withConfFiles(this.confFiles ++ confFiles)

  def mapResolutionParams(f: ResolutionParams => ResolutionParams): Resolve[F] =
    withResolutionParams(f(resolutionParams))

  def transformResolution(f: F[Resolution] => F[Resolution]): Resolve[F] =
    withThroughOpt(Some(throughOpt.fold(f)(_ andThen f)))
  def noTransformResolution(): Resolve[F] =
    withThroughOpt(None)
  def withTransformResolution(fOpt: Option[F[Resolution] => F[Resolution]]): Resolve[F] =
    withThroughOpt(fOpt)

  def transformFetcher(f: ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]): Resolve[F] =
    withTransformFetcherOpt(Some(transformFetcherOpt.fold(f)(_ andThen f)))
  def noTransformFetcher(): Resolve[F] =
    withTransformFetcherOpt(None)
  def withTransformFetcher(fOpt: Option[ResolutionProcess.Fetch[F] => ResolutionProcess.Fetch[F]])
    : Resolve[F] =
    withTransformFetcherOpt(fOpt)

  private def allMirrors0 =
    mirrors ++
      mirrorConfFiles.flatMap(_.mirrors()) ++
      confFiles.flatMap(Resolve.confFileMirrors)

  def allMirrors: F[Seq[Mirror]] =
    S.delay(allMirrors0)

  private def fetchVia: F[ResolutionProcess.Fetch[F]] = {
    val fetchs = cache.fetchs
    finalRepositories.map(r => ResolutionProcess.fetch(r, fetchs.head, fetchs.tail)(S))
  }

  private def ioWithConflicts0(fetch: ResolutionProcess.Fetch[F])
    : F[(Resolution, Seq[UnsatisfiedRule])] = {

    val initialRes = Resolve.initialResolution(
      finalDependencies,
      resolutionParams,
      initialResolution,
      mapDependenciesOpt
    )

    def run(res: Resolution): F[Resolution] = {
      val t = Resolve.runProcess(res, fetch, resolutionParams.maxIterations, cache.loggerOpt)(S)
      through(t)
    }

    def validate0(res: Resolution): F[Resolution] =
      Resolve.validate(res).either match {
        case Left(errors) =>
          val err = ResolutionError.from(errors.head, errors.tail: _*)
          S.fromAttempt(Left(err))
        case Right(()) =>
          S.point(res)
      }

    def recurseOnRules(
      res: Resolution,
      rules: Seq[(Rule, RuleResolution)]
    ): F[(Resolution, List[UnsatisfiedRule])] =
      rules match {
        case Seq() =>
          S.point((res, Nil))
        case Seq((rule, ruleRes), t @ _*) =>
          rule.enforce(res, ruleRes) match {
            case Left(c) =>
              S.fromAttempt(Left(c))
            case Right(Left(c)) =>
              recurseOnRules(res, t).map {
                case (res0, conflicts) =>
                  (res0, c :: conflicts)
              }
            case Right(Right(None)) =>
              recurseOnRules(res, t)
            case Right(Right(Some(newRes))) =>
              run(newRes.withDependencySet(DependencySet.empty)).flatMap(validate0).flatMap {
                res0 =>
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

    for {
      res0 <- run(initialRes)
      res1 <- validate0(res0)
      t    <- recurseOnRules(res1, resolutionParams.actualRules)
      (res2, conflicts) = t
      _ <- validateAllRules(res2, resolutionParams.actualRules)
    } yield (res2, conflicts)
  }

  def ioWithConflicts: F[(Resolution, Seq[UnsatisfiedRule])] =
    fetchVia.flatMap { f =>
      val fetchVia0 = transformFetcher(f)
      ioWithConflicts0(fetchVia0)
    }

  def io: F[Resolution] =
    ioWithConflicts.map(_._1)

}

object Resolve extends PlatformResolve {

  def apply(): Resolve[Task] =
    Resolve(Cache.default)

  implicit class ResolveTaskOps(private val resolve: Resolve[Task]) extends AnyVal {

    def future()(implicit ec: ExecutionContext = resolve.cache.ec): Future[Resolution] =
      resolve.io.future()

    def either()(implicit
      ec: ExecutionContext = resolve.cache.ec
    ): Either[ResolutionError, Resolution] = {

      val f = resolve
        .io
        .map(Right(_))
        .handle { case ex: ResolutionError => Left(ex) }
        .future()

      Await.result(f, Duration.Inf)
    }

    def run()(implicit ec: ExecutionContext = resolve.cache.ec): Resolution = {
      val f = future()(ec)
      Await.result(f, Duration.Inf)
    }

  }

  private[coursier] def initialResolution(
    dependencies: Seq[Dependency],
    params: ResolutionParams = ResolutionParams(),
    initialResolutionOpt: Option[Resolution] = None,
    mapDependenciesOpt: Option[Dependency => Dependency] = None
  ): Resolution = {
    import coursier.core.{Resolution => CoreResolution}

    val scalaOrg =
      if (params.typelevel) Organization("org.typelevel")
      else Organization("org.scala-lang")

    val forceScalaVersions =
      if (params.doForceScalaVersion)
        if (params.selectedScalaVersion.startsWith("3"))
          Seq(
            Module(
              scalaOrg,
              ModuleName("scala3-library_3"),
              Map.empty
            ) -> params.selectedScalaVersion,
            Module(
              scalaOrg,
              ModuleName("scala3-compiler_3"),
              Map.empty
            ) -> params.selectedScalaVersion
          )
        else
          Seq(
            Module(scalaOrg, ModuleName("scala-library"), Map.empty) -> params.selectedScalaVersion,
            Module(
              scalaOrg,
              ModuleName("scala-compiler"),
              Map.empty
            )                                                        -> params.selectedScalaVersion,
            Module(scalaOrg, ModuleName("scala-reflect"), Map.empty) -> params.selectedScalaVersion,
            Module(scalaOrg, ModuleName("scalap"), Map.empty)        -> params.selectedScalaVersion
          )
      else
        Nil

    val mapDependencies = {
      val l = mapDependenciesOpt.toSeq ++
        (if (params.typelevel) Seq(Typelevel.swap) else Nil) ++
        (if (params.doForceScalaVersion)
           Seq(CoreResolution.overrideScalaModule(params.selectedScalaVersion, scalaOrg))
         else Nil) ++
        (if (params.doOverrideFullSuffix)
           Seq(CoreResolution.overrideFullSuffix(params.selectedScalaVersion))
         else Nil)

      l.reduceOption((f, g) => dep => f(g(dep)))
    }

    val reconciliation: Option[Module => Reconciliation] = {
      val actualReconciliation = params.actualReconciliation
      if (actualReconciliation.isEmpty) None
      else
        Some {
          val cache = new ConcurrentHashMap[Module, Reconciliation]
          m =>
            val reconciliation = cache.get(m)
            if (reconciliation == null) {
              val rec = actualReconciliation.find(_._1.matches(m)) match {
                case Some((_, r)) => r
                case None         => Reconciliation.Default
              }
              val prev = cache.putIfAbsent(m, rec)
              if (prev == null)
                rec
              else
                prev
            }
            else
              reconciliation
        }
    }

    val baseRes = initialResolutionOpt.getOrElse(Resolution())

    baseRes
      .withRootDependencies(dependencies)
      .withDependencySet(DependencySet.empty)
      .withForceVersions(params.forceVersion ++ forceScalaVersions)
      .withConflicts(Set.empty)
      .withFilter(Some((dep: Dependency) => params.keepOptionalDependencies || !dep.optional))
      .withReconciliation(reconciliation)
      .withOsInfo(
        params.osInfoOpt.getOrElse {
          if (params.useSystemOsInfo)
            // call from Sync[F].delay?
            Activation.Os.fromProperties(sys.props.toMap)
          else
            Activation.Os.empty
        }
      )
      .withJdkVersion(
        params.jdkVersionOpt.orElse {
          if (params.useSystemJdkVersion)
            // call from Sync[F].delay?
            sys.props.get("java.version").flatMap(coursier.core.Parse.version)
          else
            None
        }
      )
      .withUserActivations(
        if (params.profiles.isEmpty) None
        else Some(params.profiles.iterator.map(p =>
          if (p.startsWith("!")) p.drop(1) -> false else p -> true
        ).toMap)
      )
      .withMapDependencies(mapDependencies)
      .withExtraProperties(params.properties)
      .withForceProperties(params.forcedProperties)
      .withDefaultConfiguration(params.defaultConfiguration)
      .withKeepProvidedDependencies(params.keepProvidedDependencies.getOrElse(false))
      .withForceDepMgmtVersions(params.forceDepMgmtVersions.getOrElse(false))
  }

  private[coursier] def runProcess[F[_]](
    initialResolution: Resolution,
    fetch: ResolutionProcess.Fetch[F],
    maxIterations: Int = 200,
    loggerOpt: Option[CacheLogger] = None
  )(implicit S: Sync[F]): F[Resolution] = {

    val task = ResolutionProcess(initialResolution).run(fetch, maxIterations)

    loggerOpt match {
      case None         => task
      case Some(logger) => logger.using(task)
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
            res.conflicts
          )
        )

    checkDone.zip(checkErrors, checkConflicts).map {
      case ((), (), ()) =>
    }
  }

}
