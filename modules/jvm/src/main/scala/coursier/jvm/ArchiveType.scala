package coursier.jvm

sealed abstract class ArchiveType extends Product with Serializable

object ArchiveType {

  case object Zip extends ArchiveType
  case object Tgz extends ArchiveType

  def parse(input: String): Option[ArchiveType] =
    input match {
      case "zip" => Some(Zip)
      case "tgz" => Some(Tgz)
      case _ => None
    }
}
