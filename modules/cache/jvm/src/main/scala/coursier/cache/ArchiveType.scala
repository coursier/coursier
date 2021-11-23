package coursier.cache

sealed abstract class ArchiveType extends Product with Serializable {
  def singleFile: Boolean = false
}

object ArchiveType {

  case object Zip extends ArchiveType
  case object Tgz extends ArchiveType
  case object Gzip extends ArchiveType {
    override def singleFile: Boolean = true
  }

  def parse(input: String): Option[ArchiveType] =
    input match {
      case "zip" => Some(Zip)
      case "tgz" => Some(Tgz)
      case "gz"  => Some(Gzip)
      case _     => None
    }
}
