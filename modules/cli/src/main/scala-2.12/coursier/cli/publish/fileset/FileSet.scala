package coursier.cli.publish.fileset

import java.time.Instant

import coursier.cli.publish.Content
import coursier.core.{ModuleName, Organization}
import coursier.util.Task

final case class FileSet(elements: Seq[(FileSet.Path, Content)]) {
  def ++(other: FileSet): FileSet = {
    // complexity possibly not too optimal… (removeAll iterates on all elements)
    val cleanedUp = other.elements.map(_._1).foldLeft(this)(_.removeAll(_))
    FileSet(cleanedUp.elements ++ other.elements)
  }
  def filterOutExtension(extension: String): FileSet = {
    val suffix = "." + extension
    FileSet(elements.filter(_._1.elements.lastOption.forall(!_.endsWith(suffix))))
  }
  def isEmpty: Boolean =
    elements.isEmpty

  /** Removes anything looking like a checksum or signature related to `path` */
  def removeAll(path: FileSet.Path): FileSet = {

    val prefix = path.repr + "."
    val (remove, keep) = elements.partition {
      case (p, _) =>
        p == path || p.repr.startsWith(prefix)
    }

    if (remove.isEmpty)
      this
    else
      FileSet(keep)
  }

  def update(path: FileSet.Path, content: Content): FileSet =
    ++(FileSet(Seq(path -> content)))

  def updateMetadata(
    org: Option[Organization],
    name: Option[ModuleName],
    version: Option[String],
    now: Instant
  ): Task[FileSet] =
    Task.gather.gather {
      Group.split(this)
        .map {
          case m: Group.Module =>
            m.updateMetadata(org, name, version, now)
          case m: Group.MavenMetadata =>
            m.updateMetadata(org, name, version, version.filter(!_.endsWith("SNAPSHOT")), version.toSeq, now)
        }
    }.flatMap { groups =>
      Group.merge(groups) match {
        case Left(e) => Task.fail(new Exception(e))
        case Right(fs) => Task.point(fs)
      }
    }
}

object FileSet {

  val empty = FileSet(Nil)

  final case class Path(elements: Seq[String]) {
    def /(elem: String): Path =
      Path(elements :+ elem)
    def mapLast(f: String => String): Path =
      Path(elements.dropRight(1) ++ elements.lastOption.map(f).toSeq)
    def dropLast: Path =
      Path(elements.init)
    def repr: String =
      elements.mkString("/")
  }

}
