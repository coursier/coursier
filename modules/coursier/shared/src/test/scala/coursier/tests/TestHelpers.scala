package coursier.tests

import java.lang.{Boolean => JBoolean}

import coursier.core.{Classifier, Dependency, Module, Resolution, Type}
import coursier.params.ResolutionParams
import coursier.testcache.TestCache
import coursier.util.Artifact

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object TestHelpers extends PlatformTestHelpers {

  implicit def ec: ExecutionContext =
    cache.ec

  private def dependenciesConsistencyCheck(res: Resolution): Unit = {

    def list(deps: Iterable[Dependency]): Seq[String] =
      deps
        .iterator
        .map(dep => s"${dep.module.repr}:${dep.version}") // Add config too?
        .toArray
        .sorted
        .distinct
        .toSeq

    val fromOrdered   = list(res.orderedDependencies)
    val fromMinimized = list(res.minDependencies)

    if (fromOrdered != fromMinimized) {
      val msg = "Ordered and minimized dependency lists differ"
      System.err.println(s"$msg")
      maybePrintConsistencyDiff(fromOrdered, fromMinimized)
      throw new Exception(msg)
    }
  }

  private def validate(
    name: String,
    res: Resolution,
    params: ResolutionParams,
    extraKeyPart: String = ""
  )(
    result: => Seq[String]
  ): Future[Unit] = async {
    assert(res.rootDependencies.nonEmpty)

    dependenciesConsistencyCheck(res)

    val rootDep = res.rootDependencies.head

    val simpleRootDependencies = Seq(
      Dependency(rootDep.module, rootDep.version)
        .withConfiguration(rootDep.configuration)
    )
    val isSimple = res.rootDependencies == simpleRootDependencies

    val attrPathPart =
      if (rootDep.module.attributes.isEmpty)
        ""
      else
        "/" +
          rootDep
            .module
            .attributes
            .toVector
            .sorted
            .map {
              case (k, v) => k + "_" + v
            }
            .mkString("_")

    val dependenciesHashPart =
      if (isSimple) ""
      else {
        val repr =
          if (res.rootDependencies.lengthCompare(1) == 0) res.rootDependencies.head.toString
          else res.rootDependencies.toString
        "_dep" + sha1(repr)
      }

    val bomDependenciesHashPart =
      if (res.bomDependencies.isEmpty) ""
      else "_bomDep" + sha1(res.bomDependencies.toString)

    val paramsPart =
      if (params == ResolutionParams())
        ""
      else {
        import coursier.core.Configuration
        // hack not to have to edit / review lots of test fixtures
        val params0 =
          if (params.defaultConfiguration == Configuration.defaultCompile)
            params.withDefaultConfiguration(Configuration.compile)
          else if (params.defaultConfiguration == Configuration.compile)
            params.withDefaultConfiguration(Configuration("really-compile"))
          else
            params
        // This avoids some sha1 changes
        def normalize(s: String): String = {
          val noComma = s.replace(", ", "||")
          val remove  = Seq("None", "List()", "Map()", "Set()")
          var value   = noComma.replace("HashSet", "Set")
          for (r <- remove) {
            value = value.replace("|" + r + "|", "")
            if (value.endsWith("||" + r + ")"))
              value = value.stripSuffix("||" + r + ")") + ")"
          }
          value
        }
        val n = normalize(params0.toString)
        "_params" + sha1(n)
      }

    val path = Seq(
      testDataDir,
      name,
      rootDep.module.organization.value,
      rootDep.module.name.value,
      attrPathPart,
      rootDep.version + (
        if (rootDep.configuration.isEmpty)
          ""
        else
          "_" + rootDep.configuration.value.replace('(', '_').replace(')', '_')
      ) + dependenciesHashPart + bomDependenciesHashPart + paramsPart + extraKeyPart
    ).filter(_.nonEmpty).mkString("/")

    def tryRead = textResource(path)

    val result0 = result.map { r =>
      r.replace(handmadeMetadataBase, "file:///handmade-metadata/")
    }

    val expected =
      await(
        tryRead.recoverWith {
          case _: Exception if TestCache.updateSnapshots =>
            maybeWriteTextResource(path, result0.mkString("\n"))
            tryRead
        }
      ).split('\n').toSeq.filter(_.nonEmpty)

    if (TestCache.updateSnapshots) {
      if (result0 != expected)
        maybeWriteTextResource(path, result0.mkString("\n"))
    }
    else {
      if (result0 != expected) {
        println(s"In $path:")
        for (((e, r), idx) <- expected.zip(result0).zipWithIndex if e != r)
          println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")
      }

      assert(result0 == expected)
    }
  }

  def dependenciesWithRetainedVersion(res: Resolution): Seq[Dependency] =
    res.orderedDependencies.map { dep =>
      val version = res.projectCache
        .get(dep.moduleVersion)
        .map(_._2.actualVersion)
        .orElse {
          res.reconciledVersions.get(dep.module)
        }
        .getOrElse {
          System.err.println(s"Project not found for ${dep.module.repr}:${dep.version}")
          for ((mod, v) <- res.projectCache.keys.toVector.sortBy(_.toString()))
            System.err.println(s"  ${mod.repr}:$v")
          dep.version
        }
      dep.withVersion(version)
    }

  def validateDependencies(
    res: Resolution,
    params: ResolutionParams = ResolutionParams(),
    extraKeyPart: String = ""
  ): Future[Unit] =
    validate("resolutions", res, params, extraKeyPart) {
      val elems = dependenciesWithRetainedVersion(res)
        .map { dep =>
          (
            dep.module.organization.value,
            dep.module.nameWithAttributes,
            dep.version,
            dep.configuration.value
          )
        }

      elems
        .map {
          case (org, name, ver, cfg) =>
            Seq(org, name, ver, cfg).mkString(":")
        }
    }

  def versionOf(res: Resolution, mod: Module): Option[String] =
    res
      .minDependencies
      .collectFirst {
        case dep if dep.module == mod =>
          res
            .projectCache
            .get(dep.moduleVersion)
            .fold(dep.version)(_._2.actualVersion)
      }

  def validateArtifacts(
    res: Resolution,
    artifacts: Seq[Artifact],
    params: ResolutionParams = ResolutionParams(),
    classifiers: Set[Classifier] = Set(),
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = Set(),
    extraKeyPart: String = ""
  ): Future[Unit] = {

    val classifiersPart =
      if (classifiers.isEmpty)
        ""
      else
        "_classifiers_" + sha1(classifiers.toVector.sorted.mkString("|"))

    val mainArtifactsPart =
      Option(mainArtifacts) match {
        case None    => ""
        case Some(b) => s"_main_$b"
      }

    val artifactTypesPart =
      if (artifactTypes.isEmpty)
        ""
      else
        "_types_" + sha1(artifactTypes.toVector.sorted.mkString("|"))

    validate(
      "artifacts",
      res,
      params,
      mainArtifactsPart + classifiersPart + artifactTypesPart + extraKeyPart
    ) {
      artifacts.map(_.url).distinct
    }
  }

}
