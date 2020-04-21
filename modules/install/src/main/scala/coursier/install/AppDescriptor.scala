package coursier.install

import java.io.File

import coursier.cache.{Cache, CacheLogger}
import coursier.core.{Classifier, Module, Repository, Resolution, Type, Version, VersionConstraint}
import coursier.{Dependency, Fetch, moduleString}
import coursier.params.ResolutionParams
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import coursier.util.{Artifact, Task}
import dataclass._
import coursier.core.Parse
import coursier.core.Latest

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
  prebuiltLauncher: Option[String] = None
) {
  def overrideVersion(ver: String): AppDescriptor =
    withDependencies {
      if (dependencies.isEmpty)
        dependencies
      else {
        val dep = dependencies.head.withUnderlyingDependency(_.withVersion(ver))
        dep +: dependencies.tail
      }
    }
  def mainVersionOpt: Option[String] =
    dependencies.headOption.map(_.version)


  def artifacts(
    cache: Cache[Task],
    verbosity: Int
  ): AppArtifacts = {

    // FIXME A bit of duplication with retainedMainVersion above
    val platformOpt = launcherType match {
      case LauncherType.ScalaNative => Some(Platform.Native)
      case _ => None
    }

    val (scalaVersion, platformSuffixOpt, deps) = processDependencies(
      cache,
      platformOpt,
      verbosity
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(t) => t
    }

    val hasFullCrossVersionDeps = dependencies.exists {
      case s: JavaOrScalaDependency.ScalaDependency => s.fullCrossVersion
      case _ => false
    }

    val resolutionParams = ResolutionParams()
      .withScalaVersionOpt(Some(scalaVersion).filter(_ => hasFullCrossVersionDeps))

    val res: Fetch.Result = Fetch()
      .withDependencies(deps)
      .withRepositories(repositories)
      .withResolutionParams(resolutionParams)
      .withCache(cache)
      .withMainArtifacts(mainArtifacts)
      .withClassifiers(classifiers)
      .withArtifactTypes(artifactTypes)
      .ioResult
      .unsafeRun()(cache.ec)

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
        val subRes = res.resolution.subset(
          sharedDependencies.map { m =>
            val module = m.module(scalaVersion)
            val ver = res.resolution.retainedVersions.getOrElse(module, "_")
            Dependency(module, ver)
          }
        )
        val l = coursier.Artifacts.artifacts0(
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
    platformOpt: Option[Platform],
    verbosity: Int
  ): Either[AppArtifacts.AppArtifactsException, (String, Option[String], Seq[Dependency])] = {

    val constraintOpt = scalaVersionOpt.map(coursier.core.Parse.versionConstraint)

    val t = {
      val onlyJavaDeps = dependencies.forall {
        case _: JavaOrScalaDependency.JavaDependency => true
        case _: JavaOrScalaDependency.ScalaDependency => false
      }
      val hasPlatformDeps = dependencies.forall {
        case _: JavaOrScalaDependency.JavaDependency => false
        case s: JavaOrScalaDependency.ScalaDependency => s.withPlatformSuffix
      }
      val platformOpt0 = platformOpt.filter(_ => hasPlatformDeps)
      if (onlyJavaDeps)
        Right((scala.util.Properties.versionNumberString, None)) // shouldn't matter… pass an invalid - unused at the end - version instead?
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
            ).map { case (v, p) => (v, Some(platform.suffix(p))) }
              .toRight(new AppArtifacts.ScalaDependenciesNotFound(scalaDeps))
          case None =>
            AppDescriptor.dependenciesMaxScalaVersion(
              cache,
              repositories,
              dependencies,
              constraintOpt,
              verbosity
            ).map(v => (v, None))
              .toRight(new AppArtifacts.ScalaDependenciesNotFound(scalaDeps))
        }
      }
    }

    t.map {
      case (scalaVersion, pfVerOpt) =>
        val l = dependencies
          .map(pfVerOpt.fold[JavaOrScalaDependency => JavaOrScalaDependency](identity)(pfVer => _.withPlatform(pfVer)))
          .map(_.dependency(scalaVersion))

        (scalaVersion, pfVerOpt, l)
    }
  }

  // TODO Change return type to Task[Option[String]] (and don't call unsafeRun via Resolve.run())
  def candidateMainVersions(
    cache: Cache[Task],
    verbosity: Int
  ): Iterator[String] = {

    // FIXME A bit of duplication with apply below
    val platformOpt = launcherType match {
      case LauncherType.ScalaNative => Some(Platform.Native)
      case _ => None
    }

    val (scalaVersion, _, deps) = processDependencies(
      cache,
      platformOpt,
      verbosity
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(t) => t
    }

    if (deps.isEmpty)
      Iterator.empty
    else {

      def versions() = coursier.Versions()
        .withModule(deps.head.module)
        .withRepositories(repositories)
        .withCache(cache)
        .result()
        .unsafeRun()(cache.ec)
        .versions

      Latest(deps.head.version) match {
        case Some(kind) =>
          versions().candidates(kind)
        case None =>
          val c = Parse.versionConstraint(deps.head.version)
          if (c.preferred.isEmpty)
            versions().candidatesInInterval(c.interval)
          else {
            val hasFullCrossVersionDeps = dependencies.exists {
              case s: JavaOrScalaDependency.ScalaDependency => s.fullCrossVersion
              case _ => false
            }

            val resolutionParams = ResolutionParams()
              .withScalaVersionOpt(Some(scalaVersion).filter(_ => hasFullCrossVersionDeps))

            val res = coursier.Resolve()
              .withDependencies(deps.take(1).map(_.withTransitive(false)))
              .withRepositories(repositories)
              .withResolutionParams(resolutionParams)
              .withCache(cache)
              .run()

            res.retainedVersions.get(deps.head.module)
              .flatMap(v => res.projectCache.get((deps.head.module, v)))
              .map(_._2.version)
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
          .projectCache
          .get(dep.moduleVersion)
          .map(_._2.actualVersion)
          .getOrElse(dep.version)
      }

    val opt = for {
      dep <- dependencies.headOption
      v <- mainVersionOpt
    } yield {
      val name = dep.module match {
        case j: JavaOrScalaModule.JavaModule => j.module.name.value
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

  /**
    * Tries to find a scala version that all passed dependencies are available for.
    */
  private def dependenciesMaxScalaVersion(
    cache: Cache[Task],
    repositories: Seq[Repository],
    dependencies: Seq[JavaOrScalaDependency],
    constraintOpt: Option[VersionConstraint],
    verbosity: Int
  ): Option[String] = {

    val okScalaVersions = modulesScalaVersions(cache, repositories, dependencies.map(_.module), verbosity)

    def scalaVersionIsOk(dep: JavaOrScalaDependency.ScalaDependency, sv: String): Boolean = {

      val dep0 = dep.dependency(sv)
      val depVersions = listVersions(cache, repositories, dep0.module)

      if (verbosity >= 2) {
        System.err.println(s"Versions for ${dep0.module}: ${depVersions.toVector.sorted.mkString(", ")}")
      }

      latestVersions(dep.version) || {
        val constraint = coursier.core.Parse.versionConstraint(dep.version)
        val preferredSet = constraint.preferred.toSet
        if (preferredSet.isEmpty)
          depVersions.exists { v =>
            constraint.interval.contains(Version(v))
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
        case _: JavaOrScalaDependency.JavaDependency => true
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
    platform: Platform
  ): Option[(String, String)] = {

    def platformVersions = platform
      .availableVersions(cache, repositories)
      .toVector
      .map(Version(_))
      .sorted
      .reverseIterator

    val it = platformVersions.flatMap { pfVer =>
      val pfSuffix = platform.suffix(pfVer.repr)
      val deps0 = dependencies.map(_.withPlatform(pfSuffix))
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
      listVersions(cache, repositories, mod"org.scala-lang:scala-library")

    if (verbosity >= 2) {
      System.err.println(s"Found ${availableScalaVersions.size} scala versions:")
      for (v <- availableScalaVersions.toVector.map(Version(_)).sorted)
        System.err.println(s"  ${v.repr}")
    }

    // FIXME Throw if scalaModules.nonEmpty && availableScalaVersions.isEmpty?

    val sets = scalaModules.map { m =>
      val base = m.baseModule.orgName + "_"
      if (verbosity >= 2)
        System.err.println(s"Completing '$base' (org: ${m.baseModule.organization.value}, name: ${m.baseModule.name.value})")
      val (n, compl) = coursier.complete.Complete(cache)
        .withRepositories(repositories)
        .withInput(base)
        .complete()
        .unsafeRun()(cache.ec)
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

  private def satisfiesConstraint(sv: Version, constraintOpt: Option[VersionConstraint], verbosity: Int): Boolean =
    constraintOpt match {
      case None =>
        true
      case Some(c) =>
        // here, either c.interval isn't VersionInterval.zero, or c.preferred is non empty, anyway
        val inInterval = c.interval.contains(sv)
        val lowerPreferredExists = c.preferred.forall(_.compare(sv) >= 0)

        lowerPreferredExists && inInterval
    }

  private[install] def listVersions(cache: Cache[Task], repositories: Seq[Repository], mod: Module): Set[String] = {

    def forRepo(repo: Repository): Set[String] = {

      val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)
      val t = for {
        _ <- Task.delay(logger.init())
        a <- repo.versions(mod, cache.fetch).run.attempt
        _ <- Task.delay(logger.stop())
        res <- Task.fromEither(a)
      } yield res

      t.unsafeRun()(cache.ec) match {
        case Left(err) =>
          // FIXME Trapped error
          Set.empty
        case Right((v, _)) =>
          v.available.toSet
      }
    }

    repositories.foldLeft(Set.empty[String])((acc, r) => acc ++ forRepo(r))
  }

  private val latestVersions = Set("latest.release", "latest.integration", "latest.stable")

}
