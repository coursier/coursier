package coursier.cli.fetch

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToString}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.core.{Attributes, Dependency, MinimizedExclusions, Module, Publication, Resolution}
import coursier.graph.DependencyTree
import coursier.util.Artifact
import coursier.version.VersionConstraint

import java.io.File

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

object JsonReport {

  final case class Report(
    conflict_resolution: ListMap[String, String],
    dependencies: Seq[DependencyEntry],
    version: String
  )

  def currentVersion: String = "0.1.0"

  final case class DependencyEntry(
    coord: String,
    file: Option[String] = None,
    directDependencies: Seq[String],
    dependencies: Seq[String],
    exclusions: Seq[String] = Nil
  )

  implicit lazy val dependencyEntryCodec: JsonValueCodec[DependencyEntry] =
    JsonCodecMaker.makeWithRequiredCollectionFields
  implicit lazy val reportCodec: JsonValueCodec[Report] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  def report(
    resolution: Resolution,
    artifacts: Seq[(Dependency, Publication, Artifact, Option[File])],
    useSlashSeparator: Boolean = false
  ): String = {

    val fileMap = artifacts
      .collect {
        case (_, _, art, Some(f)) =>
          art -> f
      }
      .toMap

    val dependencyArtifacts = artifacts.map {
      case (dep, pub, art, _) => (dep, pub, art)
    }

    val key: ((Dependency, Publication, Artifact)) => (Module, Attributes) = {
      case (dep, _, _) =>
        (dep.module, dep.attributes.normalize)
    }

    val key0: (Dependency) => (Module, Attributes) = {
      dep =>
        (dep.module, dep.attributes.normalize)
    }

    val sortKey: ((Module, Attributes)) => String = {
      case (mod, attr) =>
        Dependency.mavenPrefix(mod, attr)
    }

    val map = dependencyArtifacts.groupBy(key)

    val keys = dependencyArtifacts.map(key).distinct

    val relocationMap = resolution
      .orderedDependencies(true)
      .iterator
      .flatMap { dep =>
        val (_, proj) = resolution.projectCache0.getOrElse(
          dep.moduleVersionConstraint,
          sys.error(
            s"Cannot find ${dep.module.repr}:${dep.versionConstraint.asString} in project cache"
          )
        )
        if (proj.relocated)
          Iterator(dep.module -> proj.dependencies.head._2)
        else
          Iterator.empty
      }
      .toMap

    def dependenciesOf(dep: Dependency): Seq[Dependency] = {
      val actualDep = relocationMap.getOrElse(
        dep.module,
        dep
      )

      resolution
        .dependenciesOf(
          actualDep,
          withRetainedVersions = false,
          withReconciledVersions = true,
          withFallbackConfig = true
        )
        .map { dep0 =>
          relocationMap.getOrElse(dep0.module, dep0)
        }
    }

    val depMap = dependencyArtifacts
      .map {
        case (dep, _, _) =>
          dep -> dependenciesOf(dep)
      }
      .toMap

    val directDependenciesMap = keys
      .map {
        case key @ (mod, attr) =>
          val deps = map(key)
          val directDeps = deps
            .flatMap {
              case (dep, _, _) =>
                depMap.get(dep).getOrElse(dependenciesOf(dep))
            }
            .map { dep =>
              (dep.module, dep.attributes.normalize)
            }
            .distinct
            .filter(_ != key)
            .sortBy(sortKey)
          key -> directDeps
      }
      .toMap

    // useful for debugging
    lazy val missing = directDependenciesMap
      .map {
        case (k, l) =>
          (k, l.filterNot(directDependenciesMap.contains))
      }
      .filter(_._2.nonEmpty)
      .map {
        case ((mod, attr), l) =>
          Dependency.mavenPrefix(mod, attr) -> l.map {
            case (mod0, attr0) =>
              Dependency.mavenPrefix(mod0, attr0)
          }
      }

    val exclusionsMap = keys
      .map {
        case key @ (mod, attr) =>
          val deps = map(key)
          val excl = deps.map(_._1.minimizedExclusions).foldLeft(MinimizedExclusions.zero)(_ join _)
          key -> excl
            .toSeq()
            .map {
              case (org, name) =>
                s"${org.value}:${name.value}"
            }
            .sorted
      }
      .toMap

    def withRetainedVersion(dep: Dependency): Dependency = {
      val retainedVersion = VersionConstraint.fromVersion {
        resolution.retainedVersions.getOrElse(
          dep.module,
          sys.error(s"${dep.module.repr} not found in retained versions")
        )
      }
      if (dep.versionConstraint == retainedVersion) dep
      else dep.withVersionConstraint(retainedVersion)
    }

    val fromDepTrees = map.map {
      case (key, deps) =>
        val deps0 = deps.map(_._1).map(withRetainedVersion)
        val trees = DependencyTree(resolution, deps0)

        def allDeps(
          acc: ListBuffer[(Module, Attributes)],
          seen: Set[Dependency],
          trees: List[DependencyTree]
        ): Seq[(Module, Attributes)] =
          trees match {
            case Nil => acc.result().distinct
            case h :: t =>
              val isRelocated = relocationMap.contains(h.dependency.module)
              if (!isRelocated)
                acc += key0(h.dependency)
              allDeps(
                acc,
                seen + h.dependency,
                h.children.filter(tr => !seen(tr.dependency)).toList ::: t
              )
          }

        key -> allDeps(new ListBuffer, Set.empty, trees.toList).filter(_ != key)
    }

    def allDependenciesHelper(
      done: Map[(Module, Attributes), Seq[(Module, Attributes)]],
      remainingDirectDependencies: Map[(Module, Attributes), Seq[(Module, Attributes)]]
    ): Map[(Module, Attributes), Seq[(Module, Attributes)]] =
      if (remainingDirectDependencies.isEmpty) done
      else {
        val todo = remainingDirectDependencies.find(_._2.forall(done.contains)).map(_._1).getOrElse {
          sys.error("Cannot happen")
        }
        val allDeps =
          remainingDirectDependencies(todo) ++
            remainingDirectDependencies(todo).flatMap(done)
        val sortedAllDeps = allDeps.distinct.sortBy(sortKey)
        allDependenciesHelper(
          done + (todo -> sortedAllDeps),
          remainingDirectDependencies - todo
        )
      }

    lazy val allDependencies = allDependenciesHelper(Map.empty, directDependenciesMap)

    def coords(key: (Module, Attributes)): String = {
      val version = resolution.retainedVersions.getOrElse(
        key._1,
        sys.error(s"${key._1.repr} not found in retained versions")
      )
      s"${Dependency.mavenPrefix(key._1, key._2)}:${version.asString}"
    }

    val depEntries = keys.flatMap {
      case key @ (mod, attr) =>
        val deps = map(key)
        val files = deps
          .map {
            case (_, _, art) =>
              fileMap.get(art)
          }
          .flatten
          .map(_.getAbsolutePath)
          .distinct
        if (files.lengthCompare(1) <= 0)
          Seq(
            DependencyEntry(
              coords(key),
              files.headOption.map { path =>
                if (useSlashSeparator && File.separator == "\\")
                  path.replace("\\", "/")
                else
                  path
              },
              directDependenciesMap(key).map(coords).sorted,
              fromDepTrees.get(key).getOrElse(allDependencies(key)).map(coords).sorted,
              exclusionsMap(key)
            )
          )
        else {
          val attributesMap = deps
            .flatMap {
              case (dep, pub, art) =>
                val attr    = dep.withPublication(pub).attributes
                val fileOpt = fileMap.get(art)
                fileOpt.map((_, attr))
            }
            .groupBy(_._1)
            .map {
              case (k, l) =>
                (k.getAbsolutePath, l.map(_._2.normalize).sortBy(_.packagingAndClassifier).head)
            }
          files.map { f =>
            val attr = attributesMap(f)
            DependencyEntry(
              coords((mod, attr)),
              Some {
                if (useSlashSeparator && File.separator == "\\")
                  f.replace("\\", "/")
                else
                  f
              },
              directDependenciesMap(key).map(coords).sorted,
              fromDepTrees.get(key).getOrElse(allDependencies(key)).map(coords).sorted,
              exclusionsMap(key)
            )
          }
        }
    }

    // A map from requested org:name:version to reconciled org:name:version
    val conflictResolutionForRoots = resolution.rootDependencies.flatMap { dep =>
      val retainedVersion = resolution.retainedVersions.getOrElse(
        dep.module,
        sys.error(s"Cannot find ${dep.module.repr} in retained versions")
      )
      if (retainedVersion.asString == dep.versionConstraint.asString) Nil
      else
        Seq(
          s"${dep.module}:${dep.versionConstraint.asString}" -> s"${dep.module}:${retainedVersion.asString}"
        )
    }

    val report = Report(
      conflictResolutionForRoots.to(ListMap),
      depEntries.sortBy(_.coord),
      currentVersion
    )

    writeToString(report)
  }
}
