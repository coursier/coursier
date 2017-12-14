package coursier.cli.util

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import coursier.Artifact
import coursier.core.{Attributes, Dependency, Resolution}
import coursier.util.Print

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.ParSeq

case class JsonPrintRequirement(fileByArtifact: collection.mutable.Map[String, File], depToArtifacts: Map[Dependency, Seq[Artifact]], conflictResolutionForRoots: Map[String, String])

case class DepNode(coord: String, files: Seq[(String, String)], dependencies: ArrayBuffer[DepNode]) {
  def addChild(x: DepNode): Unit = {
    dependencies.append(x)
  }
}


/**
  *
  * @param conflict_resolution : map from requested org:name:version to reconciled org:name:version
  * @param dependencies        : Seq of `DepNode`s
  */
case class ReportNode(conflict_resolution: Map[String, String], dependencies: Seq[DepNode])

case class DepNodeV2(coord: String, files: Seq[(String, String)], dependencies: Set[String])

case class ReportNodeV2(conflict_resolution: Map[String, String], dependencies: Seq[DepNodeV2])


object JsonReport {

  def apply[T](roots: IndexedSeq[T], conflictResolutionForRoots: Map[String, String])
              (children: T => Seq[T], reconciledVersionStr: T => String, requestedVersionStr: T => String, getFiles: T => Seq[(String, String)]): String = {

    val objectMapper = {
      val mapper = new ObjectMapper with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper
    }

    val rootDeps: ParSeq[DepNodeV2] = roots.par.map(r => {

      /**
        * Same printing mechanism as [[coursier.util.Tree#recursivePrint]]
        */
      def flattenDeps(elems: Seq[T], ancestors: Set[T], acc: mutable.Set[String]): Unit = {
        val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
        for (elem <- unseenElems) {
          val depElems = children(elem)
          acc ++= depElems.map(reconciledVersionStr(_))

          if (depElems.nonEmpty) {
            flattenDeps(children(elem), ancestors + elem, acc)
          }
        }
      }

      val acc = scala.collection.mutable.Set[String]()
      flattenDeps(Seq(r), Set(), acc)
      DepNodeV2(reconciledVersionStr(r), getFiles(r), acc.toSet)

    })
    objectMapper.writeValueAsString(ReportNodeV2(conflictResolutionForRoots, rootDeps.toList))
  }

}


case class JsonElem(dep: Dependency,
                    artifacts: Seq[(Dependency, Artifact)] = Seq(),
                    jsonPrintRequirement: Option[JsonPrintRequirement],
                    resolution: Resolution,
                    colors: Boolean,
                    printExclusions: Boolean,
                    excluded: Boolean) {

  val (red, yellow, reset) =
    if (colors)
      (Console.RED, Console.YELLOW, Console.RESET)
    else
      ("", "", "")

  // This is used to printing json output
  // Seq of (classifier, file path) tuple
  lazy val downloadedFiles: Seq[(String, String)] = {
    jsonPrintRequirement match {
      case Some(req) =>
        req.depToArtifacts.getOrElse(dep, Seq())
          .map(x => (x.classifier, req.fileByArtifact.get(x.url)))
          .filter(_._2.isDefined)
          .map(x => (x._1, x._2.get.getPath))
      case None => Seq()
    }
  }

  lazy val reconciledVersion: String = resolution.reconciledVersions
    .getOrElse(dep.module, dep.version)

  // These are used to printing json output
  val reconciledVersionStr = s"${dep.module}:$reconciledVersion"
  val requestedVersionStr = s"${dep.module}:${dep.version}"

  lazy val repr =
    if (excluded)
      resolution.reconciledVersions.get(dep.module) match {
        case None =>
          s"$yellow(excluded)$reset ${dep.module}:${dep.version}"
        case Some(version) =>
          val versionMsg =
            if (version == dep.version)
              "this version"
            else
              s"version $version"

          s"${dep.module}:${dep.version} " +
            s"$red(excluded, $versionMsg present anyway)$reset"
      }
    else {
      val versionStr =
        if (reconciledVersion == dep.version)
          dep.version
        else {
          val assumeCompatibleVersions = Print.compatibleVersions(dep.version, reconciledVersion)

          (if (assumeCompatibleVersions) yellow else red) +
            s"${dep.version} -> $reconciledVersion" +
            (if (assumeCompatibleVersions || colors) "" else " (possible incompatibility)") +
            reset
        }

      s"${dep.module}:$versionStr"
    }

  lazy val children: Seq[JsonElem] =
    if (excluded)
      Nil
    else {
      val dep0 = dep.copy(version = reconciledVersion)

      val dependencies = resolution.dependenciesOf(
        dep0,
        withReconciledVersions = false
      ).sortBy { trDep =>
        (trDep.module.organization, trDep.module.name, trDep.version)
      }

      def excluded = resolution
        .dependenciesOf(
          dep0.copy(exclusions = Set.empty),
          withReconciledVersions = false
        )
        .sortBy { trDep =>
          (trDep.module.organization, trDep.module.name, trDep.version)
        }
        .map(_.moduleVersion)
        .filterNot(dependencies.map(_.moduleVersion).toSet).map {
        case (mod, ver) =>
          JsonElem(
            Dependency(mod, ver, "", Set.empty, Attributes("", ""), false, false),
            artifacts,
            jsonPrintRequirement,
            resolution,
            colors,
            printExclusions,
            excluded = true
          )
      }

      dependencies.map(JsonElem(_, artifacts, jsonPrintRequirement, resolution, colors, printExclusions, excluded = false)) ++
        (if (printExclusions) excluded else Nil)
    }
}
