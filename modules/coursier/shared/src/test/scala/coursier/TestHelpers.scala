package coursier

import coursier.params.ResolutionParams

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object TestHelpers extends PlatformTestHelpers {

  implicit def ec: ExecutionContext =
    cache.ec

  def validateDependencies(res: Resolution, params: ResolutionParams = ResolutionParams()): Future[Unit] = async {
    assert(res.rootDependencies.lengthCompare(1) == 0) // only fine with a single root dependency for now

    val rootDep = res.rootDependencies.head

    // only simple forms of dependencies are supported for now
    assert(rootDep == Dependency(rootDep.module, rootDep.version, configuration = rootDep.configuration))

    val attrPathPart =
      if (rootDep.module.attributes.isEmpty)
        ""
      else
        "/" + rootDep.module.attributes.toVector.sorted.map {
          case (k, v) => k + "_" + v
        }.mkString("_")

    val paramsPart =
      if (params == ResolutionParams())
        ""
      else
        "_params" + sha1(params.toString)

    val path = Seq(
      "modules/tests/shared/src/test/resources/resolutions",
      rootDep.module.organization.value,
      rootDep.module.name.value,
      attrPathPart,
      rootDep.version + (
        if (rootDep.configuration.isEmpty)
          ""
        else
          "_" + rootDep.configuration.value.replace('(', '_').replace(')', '_')
        ) + paramsPart
    ).filter(_.nonEmpty).mkString("/")

    def tryRead = textResource(path)

    // making that lazy makes scalac crash in 2.10 with scalajs
    val result = res
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

    val expected =
      await(
        tryRead.recoverWith {
          case _: Exception if writeMockData =>
            maybeWriteTextResource(path, result.mkString("\n"))
            tryRead
        }
      ).split('\n').toSeq

    for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
      println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

    assert(result == expected)
  }

}
