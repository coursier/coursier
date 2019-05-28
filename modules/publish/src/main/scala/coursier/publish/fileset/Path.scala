package coursier.publish.fileset

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
