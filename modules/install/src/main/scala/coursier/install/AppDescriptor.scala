package coursier.install

import java.io.File

import coursier.cache.{Cache, CacheLogger}
import coursier.core.{Classifier, Dependency, Module, Repository, Resolution, Type}
import coursier.Fetch
import coursier.params.ResolutionParams
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import coursier.util.{Artifact, Task}
import coursier.util.StringInterpolators._
import coursier.version.{Latest, Version, VersionConstraint, VersionParse}
import dataclass._

@data class AppDescriptor(
  repositories: Seq[Repository] = Nil,
  dependencies: Seq[JavaOrScalaDependency] = Nil,
  sharedDependencies: Seq[JavaOrScalaModule] = Nil,
  launcherType: LauncherType = LauncherType.Bootstrap,
  classifiers: Set[Classifier] = Set.empty,
  mainArtifacts: Boolean = true,
  artifactTypes: Set[Type] = Set.empty,
  mainClass: Option[String] = None,
  defaultMainClass: Option[String] = None,
  javaOptions: Seq[String] = Nil,
  javaProperties: Seq[(String, String)] = Nil,
  scalaVersionOpt: Option[String] = None,
  nameOpt: Option[String] = None,
  graalvmOptions: Option[AppDescriptor.GraalvmOptions] = None,
  @since
  prebuiltLauncher: Option[String] = None,
  @since
  jvmOptionFile: Option[String] = None,
  @since("2.0.1")
  prebuiltBinaries: Map[String, String] = Map.empty,
  @since("2.0.4")
  jna: List[String] = Nil,
  @since("2.1.0")
  versionOverrides: Seq[VersionOverride] = Nil
) {
  def overrideVersion(ver: String): AppDescriptor = {
    val overriddenDesc = VersionParse.version(ver)
      .flatMap { version =>
        versionOverrides.find(_.versionRange0.contains(version))
      }
      .map { versionOverride =>
        withRepositories(versionOverride.repositories.getOrElse(repositories))
          .withDependencies(versionOverride.dependencies.getOrElse(dependencies))
          .withMainClass(
            versionOverride.mainClass
              .map(mc => if (mc.isEmpty) None else Some(mc))
              .getOrElse(mainClass)
          )
          .withDefaultMainClass(
            versionOverride.defaultMainClass
              .map(dmc => if (dmc.isEmpty) None else Some(dmc))
              .getOrElse(defaultMainClass)
          )
          .withJavaProperties(versionOverride.javaProperties.getOrElse(javaProperties))
          .withPrebuiltLauncher {
            versionOverride.prebuiltLauncher
              .map(l => if (l.isEmpty) None else Some(l))
              .getOrElse(prebuiltLauncher)
          }
          .withPrebuiltBinaries {
            versionOverride.prebuiltBinaries
              .getOrElse(prebuiltBinaries)
          }
          .withLauncherType {
            versionOverride.launcherType
              .getOrElse(launcherType)
          }
      }
      .getOrElse(this)
    val deps = overriddenDesc.dependencies
    overriddenDesc.withDependencies {
      if (deps.isEmpty)
        deps
      else {
        val dep =
          deps.head.withUnderlyingDependency(_.withVersionConstraint(VersionConstraint(ver)))
        dep +: deps.tail
      }
    }
  }

  def mainVersionOpt: Option[VersionConstraint] =
    dependencies.headOption.map(_.versionConstraint)

  def artifacts(
    cache: Cache[Task],
    verbosity: Int
  ): AppArtifacts = {

    // FIXME A bit of duplication with retainedMainVersion above
    val platformOpt = launcherType match {
      case LauncherType.ScalaNative => Some(ScalaPlatform.Native)
      case _                        => None
    }

    val (scalaVersionOpt, platformSuffixOpt, deps) = processDependencies(
      cache,
      platformOpt,
      verbosity
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(t)  => t
    }

    val scalaVersion = scalaVersionOpt.getOrElse {
      // shouldn't matter, we should only have Java dependencies in that case
      VersionConstraint(scala.util.Properties.versionNumberString)
    }

    val hasFullCrossVersionDeps = dependencies.exists {
      case s: JavaOrScalaDependency.ScalaDependency => s.fullCrossVersion
      case _                                        => false
    }

    val resolutionParams = ResolutionParams()
      .withScalaVersionOpt0(scalaVersionOpt.filter(_ => hasFullCrossVersionDeps))

    val res: Fetch.Result = Fetch()
      .withDependencies(deps)
      .withRepositories(repositories)
      .withResolutionParams(resolutionParams)
      .withCache(cache)
      .withMainArtifacts(mainArtifacts)
      .withClassifiers(classifiers)
      .withArtifactTypes(artifactTypes)
      .ioResult
      .unsafeRun(wrapExceptions = true)(cache.ec)

    val extraProperties0 = extraProperties(res.resolution)

    if (verbosity >= 2) {
      System.err.println(s"Got ${res.artifacts.length} artifacts:")
      for (f <- res.artifacts.map(_._2.toString).sorted)
        System.err.println(s"  $f")
    }

    assert(res.extraArtifacts.isEmpty)

    val shared =
      if (sharedDependencies.isEmpty)
        List.empty[(Artifact, File)]
      else {
        val artifactMap = res.artifacts.toMap
        val subRes = res.resolution.subset0(
          sharedDependencies.map { m =>
            val module = m.module(scalaVersion.asString)
            val ver = res.resolution.retainedVersions
              .get(module)
              .map(VersionConstraint.fromVersion(_))
              .getOrElse(AppDescriptor.placeholder)
            Dependency(module, ver)
          }
        ) match {
          case Left(ex)    => throw new Exception(ex)
          case Right(res0) => res0
        }
        val l = coursier.Artifacts.artifacts(
          subRes,
          classifiers,
          Some(mainArtifacts),
          Some(artifactTypes),
          classpathOrder = true
        ).map(_._3)
        l.map { a =>
          val f = artifactMap.get(a) match {
            case Some(f0) => f0
            case None =>
              ???
          }
          a -> f
        }
      }

    AppArtifacts(res, shared, extraProperties0, platformSuffixOpt)
  }

  def processDependencies(
    cache: Cache[Task],
    platformOpt: Option[ScalaPlatform],
    verbosity: Int
  ): Either[
    AppArtifacts.AppArtifactsException,
    (Option[VersionConstraint], Option[String], Seq[Dependency])
  ] = {

    val constraintOpt = scalaVersionOpt.map(VersionParse.versionConstraint)

    val t = {
      val onlyJavaDeps = dependencies.forall {
        case _: JavaOrScalaDependency.JavaDependency  => true
        case _: JavaOrScalaDependency.ScalaDependency => false
      }
      val hasPlatformDeps = dependencies.forall {
        case _: JavaOrScalaDependency.JavaDependency  => false
        case s: JavaOrScalaDependency.ScalaDependency => s.withPlatformSuffix
      }
      val platformOpt0 = platformOpt.filter(_ => hasPlatformDeps)
      if (onlyJavaDeps)
        Right((None, None))
      else {
        def scalaDeps = dependencies.collect {
          case s: JavaOrScalaDependency.ScalaDependency =>
            s
        }
        platformOpt0 match {
          case Some(platform) =>
            AppDescriptor.dependenciesMaxScalaVersionAndPlatform(
              cache,
              repositories,
              dependencies,
              constraintOpt,
              verbosity,
              platform
            ).map { case (v, p) => (Some(v), Some(platform.suffix(p))) }
              .toRight(new AppArtifacts.ScalaDependenciesNotFound(scalaDeps))
          case None =>
            scalaVersionOpt match {
              case Some(v)
                  if v.split('.').length >= 3 && constraintOpt.forall(_.preferred.nonEmpty) =>
                Right((Some(v), None))
              case _ =>
                AppDescriptor.dependenciesMaxScalaVersion(
                  cache,
                  repositories,
                  dependencies,
                  constraintOpt,
                  verbosity
                ).map(v => (Some(v), None))
                  .toRight(new AppArtifacts.ScalaDependenciesNotFound(scalaDeps))
            }
        }
      }
    }

    t.map {
      case (scalaVersionOpt, pfVerOpt) =>
        val l = dependencies
          .map(
            pfVerOpt.fold[JavaOrScalaDependency => JavaOrScalaDependency](identity)(pfVer =>
              _.withPlatform(pfVer)
            )
          )
          // if scalaVersionOpt is empty, we should only have Java dependencies
          .map(_.dependency(scalaVersionOpt.getOrElse("")))

        (scalaVersionOpt.map(VersionConstraint(_)), pfVerOpt, l)
    }
  }

  // TODO Change return type to Task[Option[String]] (and don't call unsafeRun via Resolve.run())
  def candidateMainVersions(
    cache: Cache[Task],
    verbosity: Int
  ): Iterator[Version] = {

    // FIXME A bit of duplication with apply below
    val platformOpt = launcherType match {
      case LauncherType.ScalaNative => Some(ScalaPlatform.Native)
      case _                        => None
    }

    val (scalaVersionOpt, _, deps) = processDependencies(
      cache,
      platformOpt,
      verbosity
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(t)  => t
    }

    if (deps.isEmpty)
      Iterator.empty
    else {

      def versions() = coursier.Versions()
        .withModule(deps.head.module)
        .withRepositories(repositories)
        .withCache(cache)
        .result()
        .unsafeRun(wrapExceptions = true)(cache.ec)
        .versions

      Latest(deps.head.versionConstraint.asString) match {
        case Some(kind) =>
          versions().candidates(kind)
        case None =>
          if (deps.head.versionConstraint.preferred.isEmpty)
            versions().candidatesInInterval(deps.head.versionConstraint.interval)
          else {
            val hasFullCrossVersionDeps = dependencies.exists {
              case s: JavaOrScalaDependency.ScalaDependency => s.fullCrossVersion
              case _                                        => false
            }

            val resolutionParams = ResolutionParams()
              .withScalaVersionOpt0(scalaVersionOpt.filter(_ => hasFullCrossVersionDeps))

            val res = coursier.Resolve()
              .withDependencies(deps.take(1).map(_.withTransitive(false)))
              .withRepositories(repositories)
              .withResolutionParams(resolutionParams)
              .withCache(cache)
              .run()

            res.retainedVersions.get(deps.head.module)
              .flatMap { v =>
                res.projectCache0.get((deps.head.module, VersionConstraint.fromVersion(v)))
              }
              .map(_._2.version0)
              .iterator
          }
      }
    }
  }

  // Adds the final version of the first dependency in the java properties
  // (kind of ad hoc, that's mostly to launch mill…)
  private def extraProperties(res: Resolution): Seq[(String, String)] = {

    val mainVersionOpt = res
      .rootDependencies
      .headOption
      .map { dep =>
        res
          .projectCache0
          .get(dep.moduleVersionConstraint)
          .map(_._2.actualVersion0.asString)
          .getOrElse(dep.versionConstraint.asString)
      }

    val opt = for {
      dep <- dependencies.headOption
      v   <- mainVersionOpt
    } yield {
      val name = dep.module match {
        case j: JavaOrScalaModule.JavaModule  => j.module.name.value
        case s: JavaOrScalaModule.ScalaModule => s.baseModule.name.value
      }
      s"$name.version" -> v
    }

    opt
      .filter {
        case (k, _) =>
          // don't override a previously existing property
          !javaProperties.exists(_._1 == k)
      }
      .toSeq
  }

}

object AppDescriptor {

  @data class GraalvmOptions(
    version: Option[String] = None,
    options: Seq[String] = Nil
  )

  /** Tries to find a scala version that all passed dependencies are available for.
    */
  private def dependenciesMaxScalaVersion(
    cache: Cache[Task],
    repositories: Seq[Repository],
    dependencies: Seq[JavaOrScalaDependency],
    constraintOpt: Option[VersionConstraint],
    verbosity: Int
  ): Option[String] = {

    val okScalaVersions =
      modulesScalaVersions(cache, repositories, dependencies.map(_.module), verbosity)

    def scalaVersionIsOk(dep: JavaOrScalaDependency.ScalaDependency, sv: String): Boolean = {

      val dep0        = dep.dependency(sv)
      val depVersions = listVersions(cache, repositories, dep0.module)

      if (verbosity >= 2)
        System.err.println(
          s"Versions for ${dep0.module}: ${depVersions.toVector.sorted.mkString(", ")}"
        )

      latestVersions(dep.versionConstraint.asString) || {
        val preferredSet = dep.versionConstraint.preferred.toSet
        if (preferredSet.isEmpty)
          depVersions.exists { v =>
            dep.versionConstraint.interval.contains(Version(v))
          }
        else
          depVersions.exists { v =>
            preferredSet(Version(v))
          }
      }
    }

    val (stableVersions, unstableVersions) = okScalaVersions
      .toVector
      .map(Version(_))
      .sorted
      .partition(_.repr.forall(c => c.isDigit || c == '.'))

    val it = (stableVersions.reverseIterator ++ unstableVersions.reverseIterator).filter { sv =>
      dependencies.forall {
        case _: JavaOrScalaDependency.JavaDependency  => true
        case s: JavaOrScalaDependency.ScalaDependency => scalaVersionIsOk(s, sv.repr)
      }
    }

    val it0 = it.filter(satisfiesConstraint(_, constraintOpt, verbosity))

    if (it0.hasNext)
      Some(it0.next().repr)
    else
      None
  }

  private def dependenciesMaxScalaVersionAndPlatform(
    cache: Cache[Task],
    repositories: Seq[Repository],
    dependencies: Seq[JavaOrScalaDependency],
    constraintOpt: Option[VersionConstraint],
    verbosity: Int,
    platform: ScalaPlatform
  ): Option[(String, String)] = {

    def platformVersions = platform
      .availableVersions(cache, repositories)
      .toVector
      .map(Version(_))
      .sorted
      .reverseIterator

    val it = platformVersions.flatMap { pfVer =>
      val pfSuffix = platform.suffix(pfVer.repr)
      val deps0    = dependencies.map(_.withPlatform(pfSuffix))
      dependenciesMaxScalaVersion(cache, repositories, deps0, constraintOpt, verbosity)
        .iterator
        .map((_, pfVer.repr))
    }

    if (it.hasNext)
      Some(it.next())
    else
      None
  }

  private def modulesScalaVersions(
    cache: Cache[Task],
    repositories: Seq[Repository],
    modules: Seq[JavaOrScalaModule],
    verbosity: Int
  ): Set[String] = {

    val scalaModules = modules.collect {
      case m: JavaOrScalaModule.ScalaModule => m
    }

    val availableScalaVersions =
      listVersions(cache, repositories, mod"org.scala-lang:scala-library") ++
        listVersions(cache, repositories, mod"org.scala-lang:scala3-library_3")
          .filterNot(_.endsWith("NIGHTLY")) // Nightlies cannot be used as a "binary" version

    if (verbosity >= 2) {
      System.err.println(s"Found ${availableScalaVersions.size} scala versions:")
      for (v <- availableScalaVersions.toVector.map(Version(_)).sorted)
        System.err.println(s"  ${v.repr}")
    }

    // FIXME Throw if scalaModules.nonEmpty && availableScalaVersions.isEmpty?

    val sets = scalaModules.map { m =>
      val base = m.baseModule.orgName + "_"
      if (verbosity >= 2)
        System.err.println(
          s"Completing '$base' (org: ${m.baseModule.organization.value}, name: ${m.baseModule.name.value})"
        )
      val (n, compl) = coursier.complete.Complete(cache)
        .withRepositories(repositories)
        .withInput(base)
        .complete()
        .unsafeRun(wrapExceptions = true)(cache.ec)
      if (verbosity >= 2) {
        System.err.println(s"Found ${compl.length} completions:")
        for (c <- compl)
          System.err.println("  " + c)
      }
      val completed = compl
        .map(s => base.take(n) + s)
        .filter(_.startsWith(base)) // just in case
        .map(_.stripPrefix(base))
        .toSet
      val filter: String => Boolean =
        if (m.fullCrossVersion)
          completed
        else
          v => completed(JavaOrScalaModule.scalaBinaryVersion(v))
      if (verbosity >= 2) {
        System.err.println(s"Module $m supports ${completed.size} scala versions:")
        for (v <- completed.toVector.map(Version(_)).sorted) {
          val msg =
            if (filter(v.repr)) " (found)"
            else " (not found)"
          System.err.println(s"  ${v.repr}$msg")
        }
      }
      availableScalaVersions.filter(filter)
    }

    sets.foldLeft(availableScalaVersions)(_ intersect _)
  }

  private def satisfiesConstraint(
    sv: Version,
    constraintOpt: Option[VersionConstraint],
    verbosity: Int
  ): Boolean =
    constraintOpt match {
      case None =>
        true
      case Some(c) =>
        // here, either c.interval isn't VersionInterval.zero, or c.preferred is non empty, anyway
        val inInterval           = c.interval.contains(sv)
        val lowerPreferredExists = c.preferred.forall(_.compare(sv) >= 0)

        lowerPreferredExists && inInterval
    }

  private[install] def listVersions(
    cache: Cache[Task],
    repositories: Seq[Repository],
    mod: Module
  ): Set[String] = {

    def forRepo(repo: Repository): Set[String] = {

      val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)
      val t = for {
        _   <- Task.delay(logger.init())
        a   <- repo.versions(mod, cache.fetch).run.attempt
        _   <- Task.delay(logger.stop())
        res <- Task.fromEither(a)
      } yield res

      t.unsafeRun(wrapExceptions = true)(cache.ec) match {
        case Left(err) =>
          // FIXME Trapped error
          Set.empty
        case Right((v, _)) =>
          v.available0.map(_.repr).toSet
      }
    }

    repositories.foldLeft(Set.empty[String])((acc, r) => acc ++ forRepo(r))
  }

  private val latestVersions = Set("latest.release", "latest.integration", "latest.stable")

  private val placeholder = VersionConstraint("_")
}
