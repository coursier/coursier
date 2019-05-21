package coursier.cli.app

import java.io.File

import coursier.{Dependency, Fetch}
import coursier.cache.Cache
import coursier.cli.install.AppGenerator.NoScalaVersionFound
import coursier.core.{Artifact, Repository, Resolution, Version, VersionConstraint}
import coursier.params.ResolutionParams
import coursier.parse.JavaOrScalaModule
import coursier.util.Task

final case class AppArtifacts(
  scalaVersion: String,
  fetchResult: Fetch.Result,
  shared: Seq[(Artifact, File)],
  extraProperties: Seq[(String, String)]
)

object AppArtifacts {

  // TODO Change return type to Task[AppArtifacts] (and don't call unsafeRun)
  def apply(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int
  ): AppArtifacts = {

    val scalaVersion = maxScalaVersion(
      cache,
      desc.repositories,
      desc.dependencies.map(_.module),
      desc.scalaVersionOpt.map(coursier.core.Parse.versionConstraint),
      verbosity
    ).getOrElse {
      throw new NoScalaVersionFound
    }

    val resolutionParams = ResolutionParams()
      .withScalaVersion(scalaVersion)

    val res: Fetch.Result = coursier.Fetch()
      .withDependencies(desc.dependencies.map(_.dependency(scalaVersion)))
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
          desc.sharedDependencies.map(m => Dependency(m.module(scalaVersion), "_"))
        )
        val l = coursier.Artifacts.artifacts0(
          subRes,
          desc.classifiers,
          Some(desc.mainArtifacts),
          Some(desc.artifactTypes)
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

    AppArtifacts(scalaVersion, res, shared, extraProperties0)
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

  private def maxScalaVersion(
    cache: Cache[Task],
    repositories: Seq[Repository],
    modules: Seq[JavaOrScalaModule],
    constraintOpt: Option[VersionConstraint],
    verbosity: Int
  ): Option[String] = {

    val scalaModules = modules.collect {
      case m: JavaOrScalaModule.ScalaModule => m
    }

    val availableScalaVersions: Set[String] = {
      val base = "org.scala-lang:scala-library:"
      val (n, compl) = {
        coursier.complete.Complete(cache)
          .withRepositories(repositories)
          .withInput(base)
          .complete()
          .unsafeRun()(cache.ec)
      }
      compl
        .map(s => base.take(n) + s)
        .filter(_.startsWith(base)) // just in case
        .map(_.stripPrefix(base))
        .toSet
    }

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

    val okScalaVersions = sets.foldLeft(availableScalaVersions)(_ intersect _)

    if (verbosity >= 2) {
      System.err.println(s"Initially retained ${okScalaVersions.size} scala versions:")
      for (v <- okScalaVersions.toVector.map(Version(_)).sorted)
        System.err.println(s"  ${v.repr}")
    }

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
  }

}
