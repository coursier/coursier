package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.options.MetadataOptions
import coursier.core.{ModuleName, Organization}
import coursier.parse.DependencyParser
import coursier.publish.Pom.{Developer, License}

final case class MetadataParams(
  organization: Option[Organization],
  name: Option[ModuleName],
  version: Option[String],
  licenses: Option[Seq[License]],
  homePage: Option[String],
  // TODO Support full-fledged coursier.Dependency?
  dependencies: Option[Seq[(Organization, ModuleName, String)]],
  developersOpt: Option[Seq[Developer]]
) {
  def isEmpty: Boolean =
    organization.isEmpty &&
      name.isEmpty &&
      version.isEmpty &&
      licenses.isEmpty &&
      homePage.isEmpty &&
      dependencies.isEmpty &&
      developersOpt.isEmpty
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
            DependencyParser.moduleVersion(s, defaultScalaVersion) match {
              case Left(err) =>
                Validated.invalidNel(err)
              case Right((mod, _)) if mod.attributes.nonEmpty =>
                Validated.invalidNel(s"Dependency $s: attributes not supported for now")
              case Right((mod, ver)) =>
                Validated.validNel((mod.organization, mod.name, ver))
            }
          }
          .map(Some(_))

    val licensesV =
      if (options.license.forall(_.trim.isEmpty))
        Validated.validNel(None)
      else
        options
          .license
          .map(_.trim)
          .filter(_.nonEmpty)
          .traverse { s =>
            s.split(":", 2) match {
              case Array(id, url) =>
                Validated.validNel(License(id, url))
              case Array(id) =>
                License.map.get(id) match {
                  case None =>
                    Validated.invalidNel(s"Unrecognized license '$id', please pass an URL for it like --license $id:https://…")
                  case Some(license) =>
                    Validated.validNel(license)
                }
            }
          }
          .map(Some(_))

    val homePageOpt = options.home.map(_.trim).filter(_.nonEmpty)

    (dependenciesV, licensesV).mapN {
      (dependencies, licenses) =>
        MetadataParams(
          organization,
          name,
          version,
          licenses,
          homePageOpt,
          dependencies,
          None // developer not accepted from the command-line for now
        )
    }
  }
}
