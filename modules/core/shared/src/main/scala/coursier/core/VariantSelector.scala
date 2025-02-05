package coursier.core

import dataclass.data

sealed abstract class VariantSelector extends Product with Serializable {
  def asConfiguration: Option[Configuration]
  def isEmpty: Boolean
  final def nonEmpty: Boolean = !isEmpty

  def repr: String
}

object VariantSelector {
  @data class ConfigurationBased(configuration: Configuration) extends VariantSelector {
    def asConfiguration: Option[Configuration] = Some(configuration)
    def isEmpty: Boolean                       = configuration.isEmpty
    def repr: String                           = configuration.value
  }

  lazy val emptyConfiguration: VariantSelector =
    VariantSelector.ConfigurationBased(Configuration.empty)
}
