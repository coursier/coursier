package coursier.core

sealed abstract class Latest(val name: String) extends Product with Serializable

object Latest {
  case object Integration extends Latest("integration")
  case object Release     extends Latest("release")
  case object Stable      extends Latest("stable")

  def apply(s: String): Option[Latest] =
    s match {
      case "latest.integration" => Some(Latest.Integration)
      case "latest.release"     => Some(Latest.Release)
      case "latest.stable"      => Some(Latest.Stable)
      // Maven 2 meta versions
      case "RELEASE"            => Some(Latest.Release)
      case "LATEST"             => Some(Latest.Integration)
      case _                    => None
    }
}
