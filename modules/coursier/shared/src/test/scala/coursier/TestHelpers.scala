package coursier

import java.lang.{Boolean => JBoolean}

import coursier.params.ResolutionParams

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object TestHelpers extends PlatformTestHelpers {

  implicit def ec: ExecutionContext =
    cache.ec

  private def validate(
    name: String,
    res: Resolution,
    params: ResolutionParams,
    extraKeyPart: String = ""
  )(
    result: => Seq[String]
  ): Future[Unit] = async {
    assert(res.rootDependencies.lengthCompare(1) == 0) // only fine with a single root dependency for now

    val rootDep = res.rootDependencies.head

    val isSimple = rootDep == Dependency(rootDep.module, rootDep.version, configuration = rootDep.configuration)

    val attrPathPart =
      if (rootDep.module.attributes.isEmpty)
        ""
      else
        "/" + rootDep.module.attributes.toVector.sorted.map {
          case (k, v) => k + "_" + v
        }.mkString("_")

    val hashPart =
      if (isSimple) ""
      else s"_dep" + sha1(rootDep.toString)

    val paramsPart =
      if (params == ResolutionParams())
        ""
      else
        "_params" + sha1(params.toString)

    val path = Seq(
      s"modules/tests/shared/src/test/resources/$name",
      rootDep.module.organization.value,
      rootDep.module.name.value,
      attrPathPart,
      rootDep.version + (
        if (rootDep.configuration.isEmpty)
          ""
        else
          "_" + rootDep.configuration.value.replace('(', '_').replace(')', '_')
        ) + hashPart + paramsPart + extraKeyPart
    ).filter(_.nonEmpty).mkString("/")

    def tryRead = textResource(path)

    val result0 = result.map { r =>
      r.replace(handmadeMetadataBase, "file:///handmade-metadata/")
    }

    val expected =
      await(
        tryRead.recoverWith {
          case _: Exception if writeMockData =>
            maybeWriteTextResource(path, result0.mkString("\n"))
            tryRead
        }
      ).split('\n').toSeq

    for (((e, r), idx) <- expected.zip(result0).zipWithIndex if e != r)
      println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

    assert(result0 == expected)
  }

  def validateDependencies(res: Resolution, params: ResolutionParams = ResolutionParams()): Future[Unit] =
    validate("resolutions", res, params) {
      res
        .minDependencies
        .toVector
        .map { dep =>
          val projOpt = res.projectCache
            .get(dep.moduleVersion)
            .map { case (_, proj) => proj }
          val dep0 = dep.copy(
            version = projOpt.fold(dep.version)(_.actualVersion)
          )
          (dep0.module.organization.value, dep0.module.nameWithAttributes, dep0.version, dep0.configuration.value)
        }
        .sorted
        .distinct
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
    extraKeyPart: String = ""
  ): Future[Unit] = {

    val classifiersPart =
      if (classifiers.isEmpty)
        ""
      else
        "_classifiers_" + sha1(classifiers.toVector.sorted.mkString("|"))

    val mainArtifactsPart =
      Option(mainArtifacts) match {
        case None => ""
        case Some(b) => s"_main_$b"
      }

    validate("artifacts", res, params, mainArtifactsPart + classifiersPart + extraKeyPart) {
      artifacts
        .map(_.url)
        .sorted
    }
  }

}
