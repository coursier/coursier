package coursier.core

sealed abstract class Latest(name: String) extends Product with Serializable

object Latest {
  case object Integration extends Latest("integration")
  case object Release extends Latest("release")
  case object Stable extends Latest("stable")
}
