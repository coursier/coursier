package coursier.error

import coursier.core.{Configuration, Module, Variant, VariantSelector}
import coursier.version.{Version, VersionConstraint}

abstract class VariantError(message: String) extends DependencyError(message)

object VariantError {

  private def nl = System.lineSeparator()

  private def desc(input: Seq[(Variant.Attributes, Map[String, String])]) = input
    .map {
      case (name, map) =>
        s"${name.variantName}: {" + nl +
          map
            .toVector
            .filter(!_._1.startsWith("$"))
            .sorted
            .map {
              case (k, v) =>
                s"  $k: $v$nl"
            }
            .mkString +
          map
            .toVector
            .filter(_._1.startsWith("$"))
            .sorted
            .map {
              case (k, v) =>
                s"  $k: $v$nl"
            }
            .mkString +
          "}"
    }

  class NoVariantFound(
    module: Module,
    version: Version,
    attributes: VariantSelector.AttributesBased,
    available: Seq[(Variant.Attributes, Map[String, String])]
  ) extends VariantError(
        s"No variant found in ${module.repr}:${version.asString} for ${attributes.repr} among:" + nl +
          desc(available).mkString(nl)
      )

  class FoundTooManyVariants(
    module: Module,
    version: Version,
    attributes: VariantSelector.AttributesBased,
    retained: Seq[(Variant.Attributes, Map[String, String])]
  ) extends VariantError(
        s"Found too many variants in ${module.repr}:${version.asString} for ${attributes.repr}:" + nl +
          desc(retained).mkString(nl)
      )

  class CannotFindEquivalentVariants(
    module: Module,
    versionConstraint: VersionConstraint,
    configuration: Configuration
  ) extends VariantError(
        s"Cannot find equivalent variants for configuration ${configuration.value} of ${module.repr}:${versionConstraint.asString}"
      )
}
