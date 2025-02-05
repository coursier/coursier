package coursier.error

import coursier.core.{Module, Variant, VariantSelector}
import coursier.version.Version

abstract class VariantError(message: String) extends DependencyError(message)

object VariantError {

  private def desc(input: Seq[(Variant.Attributes, Map[String, String])]) = input
    .map {
      case (name, map) =>
        s"${name.variantName}: ${map.toVector.sorted.map { case (k, v) => s"$k: $v" }.mkString("{ ", ", ", " }")}"
    }

  class NoVariantFound(
    module: Module,
    version: Version,
    attributes: VariantSelector.AttributesBased,
    available: Seq[(Variant.Attributes, Map[String, String])]
  ) extends VariantError(
        s"No variant found in ${module.repr}:${version.asString} for ${attributes.repr} among:" + System.lineSeparator() +
          desc(available).mkString(System.lineSeparator())
      )

  class FoundTooManyVariants(
    module: Module,
    version: Version,
    attributes: VariantSelector.AttributesBased,
    retained: Seq[(Variant.Attributes, Map[String, String])]
  ) extends VariantError(
        s"Found too many variants in ${module.repr}:${version.asString} for ${attributes.repr}:" + System.lineSeparator() +
          desc(retained).mkString(System.lineSeparator())
      )
}
