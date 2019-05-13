package coursier.cli.fetch

import java.io.File

import coursier.{Artifact, Attributes, Classifier, Dependency, Resolution}
import coursier.cli.util.{JsonElem, JsonPrintRequirement, JsonReport}
import coursier.core.Publication

object JsonOutput {

  def report(
    resolution: Resolution,
    artifacts: Seq[(Dependency, Publication, Artifact)],
    files: Seq[(Artifact, File)],
    classifiers: Set[Classifier],
    exclusionsInErrors: Boolean // common.verbosityLevel >= 1
  ): String = {

    val depToArtifacts: Map[Dependency, Vector[(Publication, Artifact)]] =
      artifacts
        .groupBy(_._1)
        .mapValues(_.map(t => (t._2, t._3)).toVector)
        .iterator
        .toMap

    // TODO(wisechengyi): This is not exactly the root dependencies we are asking for on the command line, but it should be
    // a strict super set.
    val deps = depToArtifacts.keySet.toVector // ?? Use resolution.rootDependencies instead?

    // A map from requested org:name:version to reconciled org:name:version
    val conflictResolutionForRoots = resolution
      .rootDependencies
      .toVector
      .flatMap { dep =>
        val reconciledVersion = resolution.reconciledVersions.getOrElse(dep.module, dep.version)
        if (reconciledVersion == dep.version)
          Nil
        else
          Seq(s"${dep.module}:${dep.version}" -> s"${dep.module}:$reconciledVersion")
      }
      .toMap

    val artifacts0 = artifacts.map {
      case (dep, _, artifact) =>
        (dep, artifact)
    }

    val jsonReq = JsonPrintRequirement(files.map { case (a, f) => a.url -> f }.toMap, depToArtifacts)
    val roots = deps.map { d =>
      JsonElem(
        d,
        artifacts0, // ?? this corresponds to _all_ the artifacts, not just those of d
        Some(jsonReq),
        resolution,
        printExclusions = exclusionsInErrors,
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
      _.downloadedFile
    )
  }

}
