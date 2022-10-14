package coursier.params

import coursier.core.Repository

abstract class Mirror extends Serializable {
  def matches(repo: Repository): Option[Repository]
}

object Mirror {
  def replace(repositories: Seq[Repository], mirrors: Seq[Mirror]): Seq[Repository] =
    repositories
      .map { repo =>
        val it = mirrors
          .iterator
          .flatMap(_.matches(repo).iterator)
        if (it.hasNext)
          it.next()
        else
          repo
      }
      .distinct

  private def parseMirrorString(input: String): Either[String, (String, Seq[String])] =
    input.split("=", 2) match {
      case Array(dest, froms) =>
        Right((dest.trim, froms.split(";").map(_.trim).filter(_.nonEmpty)))
      case _ =>
        Left(s"Invalid mirror definition '$input', expected 'dest=source1;source2;...'")
    }

  def parse(input: String): Either[String, Mirror] =
    if (input.startsWith("tree:"))
      parseMirrorString(input.stripPrefix("tree:")).map {
        case (dest, froms) =>
          TreeMirror(froms, dest)
      }
    else if (input.startsWith("maven:"))
      parseMirrorString(input.stripPrefix("maven:")).map {
        case (dest, froms) =>
          MavenMirror(froms, dest)
      }
    else
      parseMirrorString(input).map {
        case (dest, froms) =>
          MavenMirror(froms, dest)
      }
}
