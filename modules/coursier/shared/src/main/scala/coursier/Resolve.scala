package coursier

import java.util.concurrent.ConcurrentHashMap

import coursier.cache.{Cache, CacheLogger}
import coursier.core.{
  Activation,
  BomDependency,
  Configuration,
  Dependency,
  DependencySet,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Repository,
  Resolution,
  ResolutionProcess,
  VariantSelector
}
import coursier.error.ResolutionError
import coursier.error.conflict.UnsatisfiedRule
import coursier.internal.Typelevel
import coursier.maven.MavenRepositoryLike
import coursier.params.{Mirror, MirrorConfFile, ResolutionParams}
import coursier.params.rule.{Rule, RuleResolution}
import coursier.util._
import coursier.util.Monad.ops._
import coursier.version.{ConstraintReconciliation, VersionConstraint, VersionParse}
import dataclass.{data, since}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

// format: off
@data class Resolve[F[_]](
  cache: Cache[F],
  dependencies: Seq[Dependency] = Nil,
  repositories: Seq[Repository] = Resolve.defaultRepositories,
  @deprecated("Use MirrorConfFile(...), call mirrors() on it, and set mirrors instead", "2.1.25")
  mirrorConfFiles: Seq[MirrorConfFile] = Nil,
  mirrors: Seq[Mirror] = Resolve.defaultMirrors,
  resolutionParams: ResolutionParams = ResolutionParams(),
  throughOpt: Option[F[Resolution] => F[Resolution]] = None,
  transformFetcherOpt: Option[ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]] = None,
  @since
    initialResolution: Option[Resolution] = None,
  @since
  @deprecated("For mirrors, use Resolve.confFileMirrors and set mirrors instead; for repositories, use Resolve.confFileRepositories and set repositories", "2.1.25")
    confFiles: Seq[Resolve.Path] = Nil,
  @deprecated("Unused now, repositories from default config files are read by Resolve.defaultRepositories. Use Resolve.confFileRepositories and set repositories to adjust the default repositories via config files", "2.1.25")
  preferConfFileDefaultRepositories: Boolean = true,
  @since("2.1.12")
  @deprecated("Workaround for former uses of Resolution.mapDependencies, prefer relying on ResolutionParams", "2.1.12")
    mapDependenciesOpt: Option[Dependency => Dependency] = None,
  @since("2.1.16")
  @deprecated("Use boms instead", "2.1.18")
    bomDependencies: Seq[Dependency] = Nil,
  @since("2.1.18")
  @deprecated("Use boms instead", "2.1.19")
    bomModuleVersions: Seq[(Module, String)] = Nil,
  @since("2.1.19")
    boms: Seq[BomDependency] = Nil,
  @since("2.1.25")
    gradleModuleSupport: Option[Boolean] = None
)(implicit
  sync: Sync[F]
) {
  // format: on

  private def S = sync

  private def through: F[Resolution] => F[Resolution] =
    throughOpt.getOrElse(identity[F[Resolution]])
  private def transformFetcher: ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F] =
    transformFetcherOpt.getOrElse(identity[ResolutionProcess.Fetch0[F]])

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
    val repositories0 = gradleModuleSupport match {
      case None         => repositories
      case Some(enable) =>
        repositories.map {
          case m: MavenRepositoryLike.WithModuleSupport => m.withCheckModule(enable)
          case other                                    => other
        }
    }
    allMirrors.map(Mirror.replace(repositories0, _))
  }

  def addDependencies(dependencies: Dependency*): Resolve[F] =
    withDependencies(this.dependencies ++ dependencies)
  @deprecated("Use addBom or addBomConfigs instead", "2.1.18")
  def addBomDependencies(bomDependencies: Dependency*): Resolve[F] =
    withBoms(this.boms ++ bomDependencies.map(_.asBomDependency))
  def addBom(bomModule: Module, bomVersion: VersionConstraint): Resolve[F] =
    withBoms(this.boms :+ BomDependency(bomModule, bomVersion, Configuration.empty))
  def addBom(
    bomModule: Module,
    bomVersion: VersionConstraint,
    bomConfig: Configuration
  ): Resolve[F] =
    withBoms(this.boms :+ BomDependency(bomModule, bomVersion, bomConfig))
  def addBom(bomDep: BomDependency): Resolve[F] =
    withBoms(this.boms :+ bomDep)
  def addBoms0(bomModuleVersions: (Module, VersionConstraint)*): Resolve[F] =
    withBoms(
      this.boms ++
        bomModuleVersions.map(t => BomDependency(t._1, t._2, Configuration.empty))
    )
  def addBomConfigs(boms: BomDependency*): Resolve[F] =
    withBoms(this.boms ++ boms)

  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def addBom(bomModule: Module, bomVersion: String): Resolve[F] =
    addBom(bomModule, VersionConstraint(bomVersion))
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def addBom(
    bomModule: Module,
    bomVersion: String,
    bomConfig: Configuration
  ): Resolve[F] =
    addBom(bomModule, VersionConstraint(bomVersion), bomConfig)
  @deprecated("Use addBoms0 instead", "2.1.25")
  def addBoms(bomModuleVersions: (Module, String)*): Resolve[F] =
    addBoms0(
      bomModuleVersions.map {
        case (m, v) =>
          (m, VersionConstraint(v))
      }: _*
    )

  def addRepositories(repositories: Repository*): Resolve[F] =
    withRepositories(this.repositories ++ repositories)

  def noMirrors: Resolve[F] =
    withMirrors(Nil).withMirrorConfFiles(Nil)

  def addMirrors(mirrors: Mirror*): Resolve[F] =
    withMirrors(this.mirrors ++ mirrors)

  @deprecated(
    "Unused now, parse mirror files yourself with Resolve.confFileMirrors and set mirrors instead",
    "2.1.25"
  )
  def addMirrorConfFiles(mirrorConfFiles: MirrorConfFile*): Resolve[F] =
    withMirrorConfFiles(this.mirrorConfFiles ++ mirrorConfFiles)
  @deprecated(
    "For mirrors, use Resolve.confFileMirrors and set mirrors instead; for repositories, use Resolve.confFileRepositories and set repositories",
    "2.1.25"
  )
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

  def transformFetcher(f: ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]): Resolve[F] =
    withTransformFetcherOpt(Some(transformFetcherOpt.fold(f)(_ andThen f)))
  def noTransformFetcher(): Resolve[F] =
    withTransformFetcherOpt(None)
  def withTransformFetcher(fOpt: Option[ResolutionProcess.Fetch0[F] => ResolutionProcess.Fetch0[F]])
    : Resolve[F] =
    withTransformFetcherOpt(fOpt)

  def withGradleModuleSupport(enable: Boolean): Resolve[F] =
    withGradleModuleSupport(Some(enable))

  /** Add variant attributes to be taken into account when picking Gradle Module variants
    */
  def addVariantAttributes(attributes: (String, VariantSelector.VariantMatcher)*): Resolve[F] =
    withResolutionParams(
      resolutionParams.addVariantAttributes(attributes: _*)
    )

  private def allMirrors0 =
    mirrors ++
      mirrorConfFiles.flatMap(_.mirrors()) ++
      confFiles.flatMap(Resolve.confFileMirrors)

  def allMirrors: F[Seq[Mirror]] =
    S.delay(allMirrors0)

  private def fetchVia: F[ResolutionProcess.Fetch0[F]] = {
    val fetchs = cache.fetchs
    finalRepositories.map(r => ResolutionProcess.fetch0(r, fetchs.head, fetchs.tail)(S))
  }

  private def ioWithConflicts0(fetch: ResolutionProcess.Fetch0[F])
    : F[(Resolution, Seq[UnsatisfiedRule])] = {

    val initialRes = Resolve.initialResolution(
      finalDependencies,
      resolutionParams,
      initialResolution,
      ResolveInternals.deprecatedMapDependenciesOpt(this),
      ResolveInternals.deprecatedBomDependencies(this).map(_.asBomDependency) ++
        ResolveInternals.deprecatedBomModuleVersions(this)
          .map(t => BomDependency(t._1, VersionConstraint(t._2), Configuration.empty)) ++
        boms
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

    def futureEither()(implicit
      ec: ExecutionContext = resolve.cache.ec
    ): Future[Either[ResolutionError, Resolution]] =
      resolve
        .io
        .map(Right(_))
        .handle { case ex: ResolutionError => Left(ex) }
        .future()

    def run()(implicit ec: ExecutionContext = resolve.cache.ec): Resolution = {
      val f = future()(ec)
      Await.result(f, Duration.Inf)
    }

  }

  private[coursier] def initialResolution(
    dependencies: Seq[Dependency],
    params: ResolutionParams = ResolutionParams(),
    initialResolutionOpt: Option[Resolution] = None,
    mapDependenciesOpt: Option[Dependency => Dependency] = None,
    boms: Seq[BomDependency] = Nil
  ): Resolution = {
    import coursier.core.{Resolution => CoreResolution}

    val scalaOrg =
      if (params.typelevel) Organization("org.typelevel")
      else Organization("org.scala-lang")

    val forceScalaVersions =
      if (params.doForceScalaVersion)
        if (params.selectedScalaVersionConstraint.asString.startsWith("3"))
          Seq(
            Module(
              scalaOrg,
              ModuleName("scala3-library_3"),
              Map.empty
            ) -> params.selectedScalaVersionConstraint,
            Module(
              scalaOrg,
              ModuleName("scala3-compiler_3"),
              Map.empty
            ) -> params.selectedScalaVersionConstraint
          )
        else
          Seq(
            Module(scalaOrg, ModuleName("scala-library"), Map.empty) ->
              params.selectedScalaVersionConstraint,
            Module(scalaOrg, ModuleName("scala-compiler"), Map.empty) ->
              params.selectedScalaVersionConstraint,
            Module(scalaOrg, ModuleName("scala-reflect"), Map.empty) ->
              params.selectedScalaVersionConstraint,
            Module(scalaOrg, ModuleName("scalap"), Map.empty) ->
              params.selectedScalaVersionConstraint
          )
      else
        Nil

    val mapDependencies = {
      val l = mapDependenciesOpt.toSeq ++
        (if (params.typelevel) Seq(Typelevel.swap) else Nil) ++
        (if (params.doForceScalaVersion)
           Seq(CoreResolution.overrideScalaModule(params.selectedScalaVersionConstraint, scalaOrg))
         else Nil) ++
        (if (params.doOverrideFullSuffix)
           Seq(CoreResolution.overrideFullSuffix(params.selectedScalaVersionConstraint.asString))
         else Nil)

      l.reduceOption((f, g) => dep => f(g(dep)))
    }

    val reconciliation: Option[Module => ConstraintReconciliation] = {
      val actualReconciliation = params.actualReconciliation
      if (actualReconciliation.isEmpty) None
      else
        Some {
          val cache = new ConcurrentHashMap[Module, ConstraintReconciliation]
          m =>
            val reconciliation = cache.get(m)
            if (reconciliation == null) {
              val rec = actualReconciliation.find(_._1.matches(m)) match {
                case Some((_, r)) => r
                case None         => ConstraintReconciliation.Default
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
      .withForceVersions0(params.forceVersion0 ++ forceScalaVersions)
      .withConflicts(Set.empty)
      .withFilter(Some((dep: Dependency) => params.keepOptionalDependencies || !dep.optional))
      .withReconciliation0(reconciliation)
      .withOsInfo(
        params.osInfoOpt.getOrElse {
          if (params.useSystemOsInfo)
            // call from Sync[F].delay?
            Activation.Os.fromProperties(sys.props.toMap)
          else
            Activation.Os.empty
        }
      )
      .withJdkVersion0(
        params.jdkVersionOpt0.orElse {
          if (params.useSystemJdkVersion)
            // call from Sync[F].delay?
            sys.props.get("java.version").flatMap(VersionParse.version)
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
      .withDefaultVariantAttributes(params.finalDefaultVariantAttributes)
      .withKeepProvidedDependencies(params.keepProvidedDependencies.getOrElse(false))
      .withForceDepMgmtVersions(params.forceDepMgmtVersions.getOrElse(false))
      .withEnableDependencyOverrides(
        params.enableDependencyOverrides.getOrElse(Resolution.enableDependencyOverridesDefault)
      )
      .withBoms(boms)
  }

  private[coursier] def runProcess[F[_]](
    initialResolution: Resolution,
    fetch: ResolutionProcess.Fetch0[F],
    maxIterations: Int = 200,
    loggerOpt: Option[CacheLogger] = None
  )(implicit S: Sync[F]): F[Resolution] = {

    val task = ResolutionProcess(initialResolution).run0(fetch, maxIterations)

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
      .errors0
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
