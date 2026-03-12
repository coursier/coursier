package coursier.cache

sealed abstract class ArchiveType extends Product with Serializable {
  def singleFile: Boolean = false
}

object ArchiveType {

  sealed abstract class Tar extends ArchiveType
  sealed abstract class Compressed extends ArchiveType {
    override def singleFile: Boolean = true
    def tar: Tar
  }

  case object Zip  extends ArchiveType
  case object Ar   extends ArchiveType
  case object Tar  extends Tar
  case object Tgz  extends Tar
  case object Tbz2 extends Tar
  case object Txz  extends Tar
  case object Tzst extends Tar
  case object Gzip extends Compressed {
    def tar = Tgz
  }
  case object Xz extends Compressed {
    def tar = Txz
  }

  def parse(input: String): Option[ArchiveType] =
    input match {
      case "zip"  => Some(Zip)
      case "ar"   => Some(Ar)
      case "tar"  => Some(Tar)
      case "tgz"  => Some(Tgz)
      case "tbz2" => Some(Tbz2)
      case "txz"  => Some(Txz)
      case "tzst" => Some(Tzst)
      case "gz"   => Some(Gzip)
      case "xz"   => Some(Xz)
      case _      => None
    }
  def fromMimeType(mimeType: String): Option[ArchiveType] =
    mimeType match {
      case "application/gzip" | "application/x-gzip"                      => Some(Gzip)
      case "application/xz" | "application/x-xz"                          => Some(Xz)
      case "application/tar" | "application/x-tar" | "application/x-gtar" => Some(Tar)
      case _                                                              => None
    }
}
