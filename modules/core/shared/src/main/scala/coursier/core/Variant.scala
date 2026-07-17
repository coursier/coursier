package coursier.core

import coursier.core.{Configuration => Configuration0}

sealed abstract class Variant extends Product with Serializable {
  def asConfiguration: Option[Configuration0]
  def isEmpty: Boolean
}

object Variant {
  final case class Configuration(configuration: Configuration0) extends Variant {
    lazy val asConfiguration: Option[Configuration0] =
      Some(configuration)
    def isEmpty: Boolean =
      configuration.isEmpty
  }
  final case class Attributes(variantName: String) extends Variant {
    def asConfiguration: Option[Configuration0] =
      None
    def isEmpty: Boolean =
      variantName.isEmpty
  }

  lazy val emptyConfiguration: Variant =
    Configuration(Configuration0.empty)
}
