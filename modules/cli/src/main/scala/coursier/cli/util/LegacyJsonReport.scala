package coursier.cli.util

import argonaut._
import argonaut.Argonaut._
import cats.{Eq, Eval, Show}
import cats.data.Chain
import cats.free.Cofree
import cats.syntax.foldable._
import cats.syntax.traverse._
import cats.syntax.show._
import coursier.core._
import coursier.util.Artifact

import java.io.File
import java.util.Objects

import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.immutable.ListMap
import scala.collection.mutable

/** Lookup table for files and artifacts to print in the JsonReport.
  */
final case class JsonPrintRequirement(
  fileByArtifact: Map[String, File],
  depToArtifacts: Map[Dependency, Vector[(Publication, Artifact)]]
)

/** Represents a resolved dependency's artifact in the JsonReport.
  * @param coord
  *   String representation of the artifact's maven coordinate.
  * @param file
  *   The path to the file for the artifact.
  * @param dependencies
  *   The dependencies of the artifact.
  */
final case class DepNode(
  coord: String,
  file: Option[String],
  directDependencies: List[String],
  dependencies: List[String],
  exclusions: List[String] = Nil
)

final case class ReportNode(
  conflict_resolution: ListMap[String, String],
  dependencies: Vector[DepNode],
  version: String
)

/** FORMAT_VERSION_NUMBER: Version number for identifying the export file format output. This
  * version number should change when there is a change to the output format.
  *
  * Major Version 1.x.x : Increment this field when there is a major format change Minor Version
  * x.1.x : Increment this field when there is a minor change that breaks backward compatibility for
  * an existing field or a field is removed. Patch version x.x.1 : Increment this field when a minor
  * format change that just adds information that an application can safely ignore.
  *
  * Note format changes in cli/README.md and update the Changelog section.
  */
object ReportNode {
  import argonaut.ArgonautShapeless._

  implicit def encodeListMap[K: EncodeJsonKey, V: EncodeJson]: EncodeJson[ListMap[K, V]] =
    EncodeJson { listMap =>
      val keyEncoder   = EncodeJsonKey[K]
      val valueEncoder = EncodeJson.of[V]
      jObjectAssocList {
        listMap.toList.map {
          case (k, v) => (keyEncoder.toJsonKey(k), valueEncoder(v))
        }
      }
    }

  implicit def decodeListMap[K: DecodeJson, V: DecodeJson]: DecodeJson[ListMap[K, V]] =
    DecodeJson { json =>
      val dk = DecodeJson.of[K]
      @tailrec
      def spin(x: List[JsonField], m: DecodeResult[ListMap[K, V]]): DecodeResult[ListMap[K, V]] =
        x match {
          case Nil => m
          case h :: t =>
            spin(
              t,
              for {
                mm <- m
                k  <- dk.decodeJson(jString(h))
                v  <- json.get[V](h)
              } yield mm + (k -> v)
            )
        }
      json.fields match {
        case None    => DecodeResult.fail("[K, V]ListMap[K, V]", json.history)
        case Some(s) => spin(s, DecodeResult.ok(ListMap.empty[K, V]))
      }
    }

  lazy val encodeJson = EncodeJson.of[ReportNode]
  lazy val decodeJson = DecodeJson.of[ReportNode]
  val version         = "0.1.0"
}

object LegacyJsonReport {

  private val printer = PrettyParams.nospace.copy(preserveOrder = true)

  // A dependency tree its a (A => Seq[A]) mapping and therefore it forms a Cofree[Chain, T]
  private type DependencyTree[A] = Cofree[Chain, A]
  private object DependencyTree {
    // Collapse node builds the list of dependencies by reaching to the leave first
    // and then building back the list.
    // Using Eval here as it will give us a stack-safe flatMap operation
    private def collapseNode[A: Show](node: DependencyTree[A]): Eval[Chain[String]] =
      node.foldMapM(child => Eval.now(Chain.one(child.show)))

    private def transitiveOf[A: Eq: Show](
      elem: A,
      fetchChildren: A => Seq[A]
    ): Eval[Map[A, List[String]]] = {
      val knownElems = new mutable.HashMap[String, mutable.ListBuffer[String]]
      def collectDeps(
        elem: A,
        buffer: mutable.ListBuffer[String],
        seen: mutable.HashSet[String]
      ): mutable.ListBuffer[String] = {
        val key = elem.show
        if (seen.contains(key))
          buffer
        else {
          seen.add(key)
          buffer += key
          knownElems.get(key).getOrElse {
            val deps = new mutable.ListBuffer[String]
            knownElems.put(key, deps)
            for (child <- fetchChildren(elem) if !seen.contains(child.show)) {
              deps += child.show
              deps ++= collectDeps(child, buffer, seen)
            }
            deps
          }
        }
      }
      val result =
        collectDeps(elem, new mutable.ListBuffer[String], new mutable.HashSet[String]).toList
      Eval.now(Map(elem -> result))
    }

    def flatten[A](
      roots: Vector[A],
      fetchChildren: A => Seq[A],
      retainedVersionStr: A => String
    ): Map[A, List[String]] = {
      implicit val nodeShow: Show[A] = Show.show(retainedVersionStr)
      implicit val nodeEq: Eq[A]     = Eq.by(_.show)

      Chain.fromSeq(roots)
        .traverse(transitiveOf(_, fetchChildren))
        .value
        .fold
    }
  }

  def apply[T](roots: Vector[T], conflictResolutionForRoots: ListMap[String, String])(
    children: T => Seq[T],
    retainedVersionStr: T => String,
    requestedVersionStr: T => String,
    getFile: T => Option[String],
    exclusions: T => List[String]
  ): String = {

    // Addresses the corner case in which any given library may list itself among its dependencies
    // See: https://github.com/coursier/coursier/issues/2316
    def childrenOrEmpty(elem: T): Chain[T] = {
      val elemId = retainedVersionStr(elem)
      Chain.fromSeq(children(elem).filterNot(i => retainedVersionStr(i) == elemId))
    }

    val depToTransitiveDeps =
      DependencyTree.flatten(roots, children, retainedVersionStr)

    def flattenedDeps(elem: T): List[String] =
      depToTransitiveDeps.getOrElse(elem, Nil)

    val rootDeps: Vector[DepNode] = roots.map { r =>
      DepNode(
        retainedVersionStr(r),
        getFile(r),
        childrenOrEmpty(r).iterator.map(retainedVersionStr).to(List).distinct.sorted,
        flattenedDeps(r).distinct.sorted,
        exclusions(r)
      )
    }

    val report = ReportNode(
      conflictResolutionForRoots,
      rootDeps.sortBy(_.coord),
      ReportNode.version
    )
    printer.pretty(report.asJson(ReportNode.encodeJson))
  }

}

final case class JsonElem(
  dep: Dependency,
  artifacts: Seq[(Dependency, Artifact)] = Seq(),
  jsonPrintRequirement: Option[JsonPrintRequirement],
  resolution: Resolution,
  colors: Boolean,
  printExclusions: Boolean,
  excluded: Boolean,
  overrideClassifiers: Set[Classifier]
) {

  // This is used to printing json output
  // Option of the file path
  lazy val downloadedFile: Option[String] =
    jsonPrintRequirement.flatMap(req =>
      req.depToArtifacts.getOrElse(dep, Seq()).view
        .filter(_._1.classifier == dep.attributes.classifier)
        .map(x => req.fileByArtifact.get(x._2.url))
        .filter(_.isDefined)
        .filter(_.nonEmpty)
        .map(_.get.getPath)
        .headOption
    )

  // `retainedVersion`, `retainedVersionStr`, `requestedVersionStr` are fields
  // whose values are frequently duplicated across instances of `JsonElem` and their
  // transitive `Dependencies`. Here we cast these values to `Symbol`, which is an
  // interned collection, effectively deduping the string values in memory.
  lazy val retainedVersion: String = {
    val ver = resolution.retainedVersions.getOrElse(
      dep.module,
      sys.error(s"${dep.module.repr} not found in retained versions")
    )
    Symbol(ver.asString).name
  }
  // These are used to printing json output
  lazy val retainedVersionStr = Symbol(s"${dep.mavenPrefix}:$retainedVersion").name
  val requestedVersionStr     = Symbol(s"${dep.module}:${dep.versionConstraint.asString}").name

  lazy val exclusions: List[String] = dep.minimizedExclusions
    .toSeq()
    .toList
    .sortBy {
      case (org, name) =>
        (org.value, name.value)
    }
    .map {
      case (org, name) =>
        s"${org.value}:${name.value}"
    }

  lazy val children: Vector[JsonElem] =
    if (excluded) Vector.empty
    else {
      val dependencies = resolution.dependenciesOf(
        dep,
        withRetainedVersions = false
      ).sortBy { trDep =>
        (trDep.module.organization, trDep.module.name, trDep.versionConstraint, trDep.hashCode)
      }.map { d =>
        if (overrideClassifiers.contains(dep.attributes.classifier))
          d.withAttributes(d.attributes.withClassifier(dep.attributes.classifier))
        else
          d
      }

      def calculateExclusions = resolution
        .dependenciesOf(
          dep.clearExclusions,
          withRetainedVersions = false
        )
        .view
        .sortBy { trDep =>
          (trDep.module.organization, trDep.module.name, trDep.versionConstraint, trDep.hashCode)
        }
        .map(_.moduleVersionConstraint)
        .filterNot(dependencies.map(_.moduleVersionConstraint).toSet).map {
          case (mod, ver) =>
            JsonElem(
              Dependency(mod, ver)
                .withVariantSelector(VariantSelector.emptyConfiguration)
                .clearExclusions
                .withAttributes(Attributes.empty)
                .withOptional(false)
                .withTransitive(false),
              artifacts,
              jsonPrintRequirement,
              resolution,
              colors,
              printExclusions,
              excluded = true,
              overrideClassifiers = overrideClassifiers
            )
        }

      val dependencyElems = mutable.ListBuffer.empty[JsonElem]
      val it              = dependencies.iterator
      while (it.hasNext) {
        val dep = it.next()
        val elem = JsonElem(
          dep,
          artifacts,
          jsonPrintRequirement,
          resolution,
          colors,
          printExclusions,
          excluded = false,
          overrideClassifiers = overrideClassifiers
        )
        dependencyElems += elem
      }
      dependencyElems ++ (if (printExclusions) calculateExclusions else Nil)
      dependencyElems.toVector
    }

  /** Override the hashcode to explicitly exclude `children`, because children will result in
    * recursive hash on children's children, causing performance issue. Hash collision should be
    * rare, but when that happens, the default equality check should take of the recursive aspect of
    * `children`.
    */
  override def hashCode(): Int =
    Objects.hash(dep, requestedVersionStr, retainedVersion, downloadedFile)
}
