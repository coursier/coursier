package coursier.cli.publish.dir

import coursier.cli.publish.Content

final case class DirContent(elements: Seq[(String, Content)]) {
  def ++(other: DirContent): DirContent = {
    // complexity possibly not too optimal… (removeAll iterates on all elements)
    val cleanedUp = other.elements.map(_._1).foldLeft(this)(_.removeAll(_))
    DirContent(cleanedUp.elements ++ other.elements)
  }
  def filterOutExtension(extension: String): DirContent = {
    val suffix = "." + extension
    DirContent(elements.filter(!_._1.endsWith(suffix)))
  }
  def isEmpty: Boolean =
    elements.isEmpty

  /** Removes anything looking like a checksum or signature related to `path` */
  def removeAll(name: String): DirContent = {

    val prefix = name + "."
    val (remove, keep) = elements.partition {
      case (n, _) =>
        n == name || n.startsWith(prefix)
    }

    if (remove.isEmpty)
      this
    else
      DirContent(keep)
  }

  def update(name: String, content: Content): DirContent =
    ++(DirContent(Seq(name -> content)))
}

object DirContent {

  val empty = DirContent(Nil)

}
