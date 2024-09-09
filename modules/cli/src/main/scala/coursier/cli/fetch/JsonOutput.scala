package coursier.cli.fetch

import java.io.File

import coursier.cli.util.{JsonElem, JsonPrintRequirement, JsonReport}
import coursier.core.{Classifier, Dependency, Publication, Resolution}
import coursier.util.Artifact

import scala.collection.mutable

object JsonOutput {

  def report(
    resolution: Resolution,
    artifacts: Seq[(Dependency, Publication, Artifact)],
    files: Seq[(Artifact, File)],
    classifiers: Set[Classifier],
    printExclusions: Boolean // common.verbosityLevel >= 1
  ): String = {

    val depToArtifacts: Map[Dependency, Vector[(Publication, Artifact)]] =
      artifacts
        .groupBy(_._1)
        .mapValues(_.map(t => (t._2, t._3)).toVector)
        .toMap

    // TODO(wisechengyi): This is not exactly the root dependencies we are asking for on the command line, but it should be
    // a strict super set.
    val deps = depToArtifacts.keySet.toVector // ?? Use resolution.rootDependencies instead?

    // A map from requested org:name:version to reconciled org:name:version
    val conflictResolutionForRoots = {
      val mutableMap = mutable.Map.empty[String, String]
      val it         = resolution.rootDependencies.iterator
      while (it.hasNext) {
        val dep               = it.next()
        val reconciledVersion = resolution.reconciledVersions.getOrElse(dep.module, dep.version)
        if (reconciledVersion != dep.version)
          mutableMap += s"${dep.module}:${dep.version}" -> s"${dep.module}:$reconciledVersion"
      }
      mutableMap.toMap
    }

    val artifacts0 = artifacts.map {
      case (dep, _, artifact) =>
        (dep, artifact)
    }

    val jsonReq = JsonPrintRequirement(
      files.map { case (a, f) => a.url -> f }.toMap,
      depToArtifacts
    )
    val roots = deps.map { d =>
      JsonElem(
        d,
        artifacts0, // ?? this corresponds to _all_ the artifacts, not just those of d
        Some(jsonReq),
        resolution,
        printExclusions = printExclusions,
        excluded = false,
        colors = false,
        overrideClassifiers = classifiers
      )
    }

    JsonReport(
      roots,
      conflictResolutionForRoots
    )(
      _.children,
      _.reconciledVersionStr,
      _.requestedVersionStr,
      _.downloadedFile,
      _.exclusions
    )
  }

}
