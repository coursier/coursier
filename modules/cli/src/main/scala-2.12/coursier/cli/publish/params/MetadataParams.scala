package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.options.MetadataOptions
import coursier.core.{ModuleName, Organization}
import coursier.util.Parse

final case class MetadataParams(
  organization: Option[Organization],
  name: Option[ModuleName],
  version: Option[String],
  // TODO Support full-fledged coursier.Dependency?
  dependencies: Option[Seq[(Organization, ModuleName, String)]]
) {
  def isEmpty: Boolean =
    organization.isEmpty &&
      name.isEmpty &&
      version.isEmpty &&
      dependencies.isEmpty
}

object MetadataParams {
  def apply(options: MetadataOptions, defaultScalaVersion: String): ValidatedNel[String, MetadataParams] = {

    // TODO Check for invalid character? emptiness?
    val organization = options.organization.map(Organization(_))
    val name = options.name.map(ModuleName(_))
    val version = options.version
    val dependenciesV =
      if (options.dependency.forall(_.trim.isEmpty))
        Validated.validNel(None)
      else
        options
          .dependency
          .map(_.trim)
          .filter(_.nonEmpty)
          .traverse { s =>
            Parse.moduleVersion(s, defaultScalaVersion) match {
              case Left(err) =>
                Validated.invalidNel(err)
              case Right((mod, ver)) if mod.attributes.nonEmpty =>
                Validated.invalidNel(s"Dependency $s: attributes not supported for now")
              case Right((mod, ver)) =>
                Validated.validNel((mod.organization, mod.name, ver))
            }
          }
          .map(Some(_))

    dependenciesV.map { dependencies =>
      MetadataParams(
        organization,
        name,
        version,
        dependencies
      )
    }
  }
}
