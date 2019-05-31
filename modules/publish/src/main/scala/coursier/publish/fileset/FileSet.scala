package coursier.publish.fileset

import java.time.Instant

import coursier.publish.Content
import coursier.core.{ModuleName, Organization}
import coursier.publish.Pom.{Developer, License}
import coursier.util.Task

final case class FileSet(elements: Seq[(Path, Content)]) {
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
  def removeAll(path: Path): FileSet = {

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

  def update(path: Path, content: Content): FileSet =
    ++(FileSet(Seq(path -> content)))

  def updateMetadata(
    org: Option[Organization],
    name: Option[ModuleName],
    version: Option[String],
    licenses: Option[Seq[License]],
    developers: Option[Seq[Developer]],
    homePage: Option[String],
    now: Instant
  ): Task[FileSet] = {

    val split = Group.split(this)

    val adjustOrgName =
      if (org.isEmpty && name.isEmpty)
        Task.point(split)
      else {
        val map = split.map {
          case m: Group.Module =>
            (m.organization, m.name) -> (org.getOrElse(m.organization), name.getOrElse(m.name))
          case m: Group.MavenMetadata =>
            (m.organization, m.name) -> (org.getOrElse(m.organization), name.getOrElse(m.name))
        }.toMap

        Task.gather.gather {
          split.map { m =>
            m.transform(map, now)
          }
        }
      }

    adjustOrgName.flatMap { l =>
      Task.gather.gather {
        l.map {
          case m: Group.Module =>
            m.updateMetadata(org, name, version, licenses, developers, homePage, now)
          case m: Group.MavenMetadata =>
            m.updateContent(org, name, version, version.filter(!_.endsWith("SNAPSHOT")), version.toSeq, now)
        }
      }
    }.flatMap { groups =>
      Group.merge(groups) match {
        case Left(e) => Task.fail(new Exception(e))
        case Right(fs) => Task.point(fs)
      }
    }
  }

  def order: Task[FileSet] = {

    val split = Group.split(this)

    // dependencies before dependees
    // JARs / javadoc / sources before POMs
    // MD5 / SHA1 before underlying file
    // sig before signed file
    // standard files before maven-metadata.xml
    // POMs at the end of a module

    ???
  }
}

object FileSet {

  val empty = FileSet(Nil)

}
