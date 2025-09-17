package coursier.tests

import java.lang.{Boolean => JBoolean}

import coursier.core.{
  Classifier,
  Configuration,
  Dependency,
  Module,
  Resolution,
  Type,
  VariantSelector
}
import coursier.params.ResolutionParams
import coursier.testcache.TestCache
import coursier.util.Artifact
import coursier.version.VersionConstraint

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object TestHelpers extends PlatformTestHelpers {

  implicit def ec: ExecutionContext =
    cache.ec

  private def dependenciesConsistencyCheck(res: Resolution): Unit = {

    def list(deps: Iterable[Dependency]): Seq[String] =
      deps
        .iterator
        .map(dep => s"${dep.module.repr}:${dep.versionConstraint.asString}") // Add config too?
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

  def pathFor(
    res: Resolution,
    params: ResolutionParams,
    extraKeyPart: String = "",
    // seems to make VariantSelector.AttributesBased#toString more reproducible
    // across different environments
    attributesBasedReprAsToString: Boolean = false
  ): String = {
    assert(res.rootDependencies.nonEmpty)

    val rootDep = res.rootDependencies.head

    if (attributesBasedReprAsToString)
      VariantSelector.AttributesBased.reprAsToString.set(true)
    VersionConstraint.parsedValueAsToString.set(true)
    try {

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

      val (dependenciesHashPart, bomModVerHashPart) = {
        def isSimpleDependencies(ds: Seq[Dependency]) = {
          val simpleDeps = Seq(
            Dependency(
              rootDep.module,
              rootDep.versionConstraint
            ).withVariantSelector(rootDep.variantSelector)
          )
          ds == simpleDeps
        }

        val dependencyElements = res.rootDependencies match {
          case ds if isSimpleDependencies(ds) => ""
          case ds if ds.lengthCompare(1) == 0 => ds.head
          case ds                             => ds
        }

        val bomElements = res.boms match {
          case boms if boms.isEmpty => ""
          case boms                 => boms.map {
              // quick hack to recycle former sha-1 values when config is empty
              case emptyConfigBomDep if emptyConfigBomDep.config.isEmpty =>
                emptyConfigBomDep.moduleVersionConstraint
              case other =>
                other
            }
        }

        (dependencyElements.toString(), bomElements.toString()) match {
          case ("", "")       => ("", "")
          case (dStr @ _, "") => ("_dep" + sha1(dStr), "")
          case ("", bStr @ _) => ("", "_boms" + sha1(bStr))
          // Combine dependencies and BOMs into a single hash to avoid overly long file names.
          case (dStr @ _, bStr @ _) => ("_dep" + sha1(dStr + bStr), "")
        }
      }

      val paramsPart =
        if (params == ResolutionParams())
          ""
        else {
          // hack not to have to edit / review lots of test fixtures
          val params0 =
            if (params.defaultConfiguration == Configuration.defaultRuntime)
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

      val variantPart = rootDep.variantSelector match {
        case _: VariantSelector.ConfigurationBased  => ""
        case other: VariantSelector.AttributesBased =>
          "_variant" + sha1(other.repr).take(7)
      }

      Seq(
        rootDep.module.organization.value,
        rootDep.module.name.value,
        attrPathPart,
        rootDep.versionConstraint.asString + (
          if (rootDep.variantSelector.isEmpty)
            ""
          else
            "_" +
              (rootDep.variantSelector match {
                case c: VariantSelector.ConfigurationBased =>
                  c.configuration
                    .value
                    .replace('(', '_')
                    .replace(')', '_')
                case a: VariantSelector.AttributesBased =>
                  ""
              })
        ) + dependenciesHashPart + variantPart + bomModVerHashPart + paramsPart + extraKeyPart
      ).filter(_.nonEmpty).mkString("/")
    }
    finally {
      VersionConstraint.parsedValueAsToString.remove()
      if (attributesBasedReprAsToString)
        VariantSelector.AttributesBased.reprAsToString.remove()
    }
  }

  def validate(
    name: String,
    res: Resolution,
    params: ResolutionParams,
    extraKeyPart: String = "",
    attributesBasedReprAsToString: Boolean = false
  )(
    result: => Seq[String]
  ): Future[Unit] = async {

    dependenciesConsistencyCheck(res)

    val path = Seq(
      testDataDir,
      name,
      pathFor(
        res,
        params,
        extraKeyPart,
        attributesBasedReprAsToString = attributesBasedReprAsToString
      )
    ).filter(_.nonEmpty).mkString("/")

    await(validateResult(path)(result))
  }

  def validateResult(path: String)(
    result: => Seq[String]
  ): Future[Unit] = async {

    def tryRead = textResource(path)

    val result0 = result.filter(_.nonEmpty).map { r =>
      r.replace(handmadeMetadataBase, "file:///handmade-metadata/")
    }

    val expected =
      await(
        tryRead.recoverWith {
          case _: Exception if TestCache.updateSnapshots =>
            maybeWriteTextResource(path, result0.mkString("\n"))
            tryRead
        }
      ).split('\n').toSeq

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

  def validateDependencies(
    res: Resolution,
    params: ResolutionParams = ResolutionParams(),
    extraKeyPart: String = "",
    attributesBasedReprAsToString: Boolean = false
  ): Future[Unit] =
    validate(
      "resolutions",
      res,
      params,
      extraKeyPart,
      attributesBasedReprAsToString = attributesBasedReprAsToString
    ) {
      res.orderedDependencies.map { dep =>
        Seq(
          dep.module.organization.value,
          dep.module.nameWithAttributes,
          dep.versionConstraint.asString,
          dep.variantSelector.repr
        ).mkString(":")
      }
    }

  def versionOf(res: Resolution, mod: Module): Option[String] =
    res
      .minDependencies
      .collectFirst {
        case dep if dep.module == mod =>
          res
            .projectCache0
            .get(dep.moduleVersionConstraint)
            .fold(dep.versionConstraint.asString)(_._2.actualVersion0.asString)
      }

  def validateArtifacts(
    res: Resolution,
    artifacts: Seq[Artifact],
    params: ResolutionParams = ResolutionParams(),
    classifiers: Set[Classifier] = Set(),
    artifactAttributes: Seq[VariantSelector.AttributesBased] = Nil,
    mainArtifacts: JBoolean = null,
    artifactTypes: Set[Type] = Set(),
    extraKeyPart: String = ""
  ): Future[Unit] = {

    val classifiersPart =
      if (classifiers.isEmpty)
        ""
      else
        "_classifiers_" + sha1(classifiers.toVector.sorted.mkString("|"))

    val artifactAttrPart =
      if (artifactAttributes.isEmpty)
        ""
      else
        "_artAttr_" + sha1(artifactAttributes.toVector.map(_.repr).sorted.mkString("|")).take(8)

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
      mainArtifactsPart + classifiersPart + artifactAttrPart + artifactTypesPart + extraKeyPart
    ) {
      artifacts.map(_.url).distinct
    }
  }

}
