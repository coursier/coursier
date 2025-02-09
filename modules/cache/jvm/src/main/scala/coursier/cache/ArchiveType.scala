package coursier.cache

sealed abstract class ArchiveType extends Product with Serializable {
  def singleFile: Boolean = false
}

object ArchiveType {

  sealed abstract class Tar extends ArchiveType

  case object Zip  extends ArchiveType
  case object Ar   extends Tar
  case object Tgz  extends Tar
  case object Tbz2 extends Tar
  case object Txz  extends Tar
  case object Tzst extends Tar
  case object Gzip extends ArchiveType {
    override def singleFile: Boolean = true
  }

  def parse(input: String): Option[ArchiveType] =
    input match {
      case "zip"  => Some(Zip)
      case "ar"   => Some(Ar)
      case "tgz"  => Some(Tgz)
      case "tbz2" => Some(Tbz2)
      case "txz"  => Some(Txz)
      case "tzst" => Some(Tzst)
      case "gz"   => Some(Gzip)
      case _      => None
    }
}
