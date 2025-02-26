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

    def equivalentAttributesSelector: Option[AttributesBased] =
      configuration match {
        case Configuration.compile | Configuration.defaultCompile =>
          val a = AttributesBased(
            Map(
              "org.gradle.category" -> "library",
              "org.gradle.usage"    -> "java-api"
            )
          )
          Some(a)
        case Configuration.runtime | Configuration.defaultRuntime | Configuration.default =>
          val a = AttributesBased(
            Map(
              "org.gradle.category" -> "library",
              "org.gradle.usage"    -> "java-runtime"
            )
          )
          Some(a)
        case _ => None
      }
  }

  @data class AttributesBased(
    attributes: Map[String, String] = Map.empty
  ) extends VariantSelector {
    def asConfiguration: Option[Configuration] = None
    def isEmpty: Boolean                       = attributes.isEmpty
    def repr: String =
      attributes.toVector.sorted.map { case (k, v) => s"$k=$v" }.mkString("{ ", ", ", " }")

    def matches(variantAttributes: Map[String, String]): Boolean =
      attributes.forall {
        case (k, v) =>
          variantAttributes.get(k).forall(_ == v)
      }

    def equivalentConfiguration: Option[Configuration] = {
      val isCompile = attributes
        .get("org.gradle.usage")
        .exists(v => v == "java-api" || v == "kotlin-api")
      val isRuntime = attributes
        .get("org.gradle.usage")
        .exists(v => v == "java-runtime" || v == "kotlin-runtime")
      if (isCompile) Some(Configuration.compile)
      else if (isRuntime) Some(Configuration.runtime)
      else None
    }

    def addAttributes(attributes: (String, String)*): AttributesBased =
      if (attributes.isEmpty) this
      else
        withAttributes(this.attributes ++ attributes)
  }

  lazy val emptyConfiguration: VariantSelector =
    VariantSelector.ConfigurationBased(Configuration.empty)
}
