package coursier.cache.util

import java.util.Locale

sealed abstract class Os extends Product with Serializable

object Os {
  sealed abstract class Unix extends Os

  final case class Linux(muslBased: Boolean = false) extends Unix
  case object Mac                                    extends Unix
  case object Windows                                extends Os

  def apply(osName: String): Option[Os] =
    osName.toLowerCase(Locale.ROOT) match {
      case linux if linux.contains("linux") =>
        // FIXME Detect musl
        Some(Linux())
      case mac if mac.contains("mac")             => Some(Mac)
      case windows if windows.contains("windows") => Some(Windows)
      case _                                      => None
    }

  private lazy val currentOs =
    sys.props.get("os.name").flatMap(apply(_))

  def apply(): Option[Os] =
    currentOs
  def get(): Os =
    currentOs.getOrElse {
      sys.error(s"Unrecognized OS type (${sys.props.getOrElse("os.name", "")})")
    }
}
