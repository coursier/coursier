package coursier.params

final class MirrorConfFile private (
  val path: String,
  val optional: Boolean
) extends coursier.internal.PlatformMirrorConfFile with Serializable {

  private def this(path: String) = this(path, true)

  override def equals(o: Any): Boolean = o match {
    case x: MirrorConfFile => (this.path == x.path) && (this.optional == x.optional)
    case _ => false
  }

  override def hashCode: Int =
    37 * (37 * (37 * (17 + "coursier.params.MirrorConfFile".##) + path.##) + optional.##)

  override def toString: String =
    "MirrorConfFile(" + path + ", " + optional + ")"

  private[this] def copy(path: String = path, optional: Boolean = optional): MirrorConfFile =
    new MirrorConfFile(path, optional)

  def withPath(path: String): MirrorConfFile =
    copy(path = path)
  def withOptional(optional: Boolean): MirrorConfFile =
    copy(optional = optional)
}

object MirrorConfFile {
  def apply(path: String): MirrorConfFile = new MirrorConfFile(path)
  def apply(path: String, optional: Boolean): MirrorConfFile = new MirrorConfFile(path, optional)
}
