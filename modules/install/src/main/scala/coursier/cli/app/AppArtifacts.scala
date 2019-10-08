package coursier.cli.app

import java.io.File

import coursier.{Dependency, Fetch, moduleString}
import coursier.cache.{Cache, CacheLogger}
import coursier.core.{Module, Repository, Resolution, Version, VersionConstraint}
import coursier.params.ResolutionParams
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import coursier.util.{Artifact, Task}

final case class AppArtifacts(
  fetchResult: Fetch.Result,
  shared: Seq[(Artifact, File)],
  extraProperties: Seq[(String, String)],
  platformSuffixOpt: Option[String]
)

object AppArtifacts {

  // TODO Change return type to Task[AppArtifacts] (and don't call unsafeRun)
  def apply(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int
  ): AppArtifacts = {

    val platformOpt = desc.launcherType match {
      case LauncherType.ScalaNative => Some(Platform.Native)
      case _ => None
    }

    val (scalaVersion, platformSuffixOpt, deps) = dependencies(
      cache,
      desc.repositories,
      platformOpt,
      verbosity,
      desc.dependencies,
      desc.scalaVersionOpt.map(coursier.core.Parse.versionConstraint)
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(t) => t
    }

    val hasFullCrossVersionDeps = desc.dependencies.exists {
      case s: JavaOrScalaDependency.ScalaDependency => s.fullCrossVersion
      case _ => false
    }

    val resolutionParams = ResolutionParams()
      .withScalaVersionOpt(Some(scalaVersion).filter(_ => hasFullCrossVersionDeps))

    val res: Fetch.Result = coursier.Fetch()
      .withDependencies(deps)
      .withRepositories(desc.repositories)
      .withResolutionParams(resolutionParams)
      .withCache(cache)
      .withMainArtifacts(desc.mainArtifacts)
      .withClassifiers(desc.classifiers)
      .withArtifactTypes(desc.artifactTypes)
      .ioResult
      .unsafeRun()(cache.ec)

    val extraProperties0 = extraProperties(res.resolution, desc)

    if (verbosity >= 2) {
      System.err.println(s"Got ${res.artifacts.length} artifacts:")
      for (f <- res.artifacts.map(_._2.toString).sorted)
        System.err.println(s"  $f")
    }

    assert(res.extraArtifacts.isEmpty)

    val shared =
      if (desc.sharedDependencies.isEmpty)
        List.empty[(Artifact, File)]
      else {
        val artifactMap = res.artifacts.toMap
        val subRes = res.resolution.subset(
          desc.sharedDependencies.map { m =>
            val module = m.module(scalaVersion)
            val ver = res.resolution.retainedVersions.getOrElse(module, "_")
            Dependency.of(module, ver)
          }
        )
        val l = coursier.Artifacts.artifacts0(
          subRes,
          desc.classifiers,
          Some(desc.mainArtifacts),
          Some(desc.artifactTypes),
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

  def dependencies(
    cache: Cache[Task],
    repositories: Seq[Repository],
    platformOpt: Option[Platform],
    verbosity: Int,
    javaOrScalaDeps: Seq[JavaOrScalaDependency],
    constraintOpt: Option[VersionConstraint] = None
  ): Either[String, (String, Option[String], Seq[Dependency])] = {

    val t = {
      val onlyJavaDeps = javaOrScalaDeps.forall {
        case _: JavaOrScalaDependency.JavaDependency => true
        case _: JavaOrScalaDependency.ScalaDependency => false
      }
      val hasPlatformDeps = javaOrScalaDeps.forall {
        case _: JavaOrScalaDependency.JavaDependency => false
        case s: JavaOrScalaDependency.ScalaDependency => s.withPlatformSuffix
      }
      val platformOpt0 = platformOpt.filter(_ => hasPlatformDeps)
      if (onlyJavaDeps)
        Right((scala.util.Properties.versionNumberString, None)) // shouldn't matter… pass an invalid - unused at the end - version instead?
      else
        platformOpt0 match {
          case Some(platform) =>
            AppArtifacts.dependenciesMaxScalaVersionAndPlatform(
              cache,
              repositories,
              javaOrScalaDeps,
              constraintOpt,
              verbosity,
              platform
            ).map { case (v, p) => (v, Some(platform.suffix(p))) }.toRight("No scala version found")
          case None =>
            AppArtifacts.dependenciesMaxScalaVersion(
              cache,
              repositories,
              javaOrScalaDeps,
              constraintOpt,
              verbosity
            ).map(v => (v, None)).toRight("No scala version found")
        }
    }

    t.right.map {
      case (scalaVersion, pfVerOpt) =>
        val l = javaOrScalaDeps
          .map(pfVerOpt.fold[JavaOrScalaDependency => JavaOrScalaDependency](identity)(pfVer => _.withPlatform(pfVer)))
          .map(_.dependency(scalaVersion))

        (scalaVersion, pfVerOpt, l)
    }
  }

  // Adds the final version of the first dependency in the java properties
  // (kind of ad hoc, that's mostly to launch mill…)
  private def extraProperties(res: Resolution, desc: AppDescriptor): Seq[(String, String)] = {

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
      dep <- desc.dependencies.headOption
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
          !desc.javaProperties.exists(_._1 == k)
      }
      .toSeq
  }

  private[app] def listVersions(cache: Cache[Task], repositories: Seq[Repository], mod: Module): Set[String] = {

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

  private def satisfiesConstrain(sv: Version, constraintOpt: Option[VersionConstraint], verbosity: Int): Boolean =
    constraintOpt match {
      case None =>
        true
      case Some(c) =>
        // here, either c.interval isn't VersionInterval.zero, or c.preferred is non empty, anyway
        val inInterval = c.interval.contains(sv)
        val lowerPreferredExists = c.preferred.forall(_.compare(sv) >= 0)

        lowerPreferredExists && inInterval
    }

  private def constrainedMax(okScalaVersions: Set[String], constraintOpt: Option[VersionConstraint], verbosity: Int): Option[String] =
    if (okScalaVersions.isEmpty)
      None
    else
      constraintOpt match {
        case None =>
          Some(okScalaVersions.map(Version(_)).max.repr)
        case Some(c) =>
          // here, either c.interval isn't VersionInterval.zero, or c.preferred is non empty, anyway
          val okScalaVersions0 = okScalaVersions
            .map(Version(_))
            .filter(v => c.interval.contains(v))
          val okScalaVersions1 = c.preferred.foldLeft(okScalaVersions0) {
            (set, pref) =>
              set.filter(_.compare(pref) >= 0)
          }

          if (verbosity >= 2) {
            System.err.println(s"Retained ${okScalaVersions1.size} scala versions:")
            for (v <- okScalaVersions1.toVector.sorted)
              System.err.println(s"  ${v.repr}")
          }

          if (okScalaVersions1.isEmpty)
            None
          else
            Some(okScalaVersions1.max.repr)
      }

  /**
    * Tries to find a scala version that all passed modules are available for.
    *
    * Should download at most one file listing per module, plus one to list scala versions themselves.
    */
  def modulesMaxScalaVersion(
    cache: Cache[Task],
    repositories: Seq[Repository],
    modules: Seq[JavaOrScalaModule],
    constraintOpt: Option[VersionConstraint],
    verbosity: Int
  ): Option[String] = {

    val okScalaVersions = modulesScalaVersions(cache, repositories, modules, verbosity)

    if (verbosity >= 2) {
      System.err.println(s"Initially retained ${okScalaVersions.size} scala versions:")
      for (v <- okScalaVersions.toVector.map(Version(_)).sorted)
        System.err.println(s"  ${v.repr}")
    }

    constrainedMax(okScalaVersions, constraintOpt, verbosity)
  }

  private val latestVersions = Set("latest.release", "latest.integration", "latest.stable")

  /**
    * Tries to find a scala version that all passed dependencies are available for.
    */
  def dependenciesMaxScalaVersion(
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

    val it0 = it.filter(satisfiesConstrain(_, constraintOpt, verbosity))

    if (it0.hasNext)
      Some(it0.next().repr)
    else
      None
  }

  def dependenciesMaxScalaVersionAndPlatform(
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

}
