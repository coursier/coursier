package coursier.cli.publish.conf

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import argonaut._
import argonaut.Argonaut._
import coursier.core.Organization
import coursier.publish.Pom.License

final case class Conf(
  organization: OrganizationDetails,
  version: Option[String],
  homePage: Option[String],
  licenses: Option[List[License]],
  developers: Option[List[coursier.publish.Pom.Developer]]
)

object Conf {

  private implicit val licenseDecoder: DecodeJson[License] =
    DecodeJson { c =>
      c.as[String].flatMap { l =>
        l.split(":", 2) match {
          case Array(id, url) =>
            DecodeResult.ok(License(id, url))
          case Array(id) =>
            License.map.get(id) match {
              case None =>
                DecodeResult.fail(s"Unrecognized license: '$id'", c.history)
              case Some(l) =>
                DecodeResult.ok(l)
            }
        }
      }
    }

  private final case class SimpleFields(
    version: Option[String] = None,
    ver: Option[String] = None,
    homePage: Option[String] = None,
    home: Option[String] = None,
    developers: Option[List[Developer]] = None,
    licenses: Option[List[License]] = None
  ) {
    def versionOpt =
      version.orElse(ver)
    def homePageOpt =
      homePage.orElse(home)
  }

  private object SimpleFields {
    import argonaut.ArgonautShapeless._
    implicit val decoder = DecodeJson.of[SimpleFields]
  }

  private val orgDetailsDecoder: DecodeJson[Option[OrganizationDetails]] =
    DecodeJson { c =>

      c.as(SimpleFields.decoder).flatMap { s =>

        val orgField = c.downField("organization").success
          .orElse(c.downField("organisation").success)
          .orElse(c.downField("org").success)

        orgField match {
          case None => DecodeResult.ok(Option.empty[OrganizationDetails])
          case Some(c) =>

            val organizationAsString = orgField
              .flatMap(_.as[String].toOption)
              .map { s =>
                OrganizationDetails(Some(Organization(s)), None)
              }
            def organizationAsObj = orgField
              .flatMap(_.as(OrganizationDetails.decoder).toOption)

            organizationAsString
              .orElse(organizationAsObj) match {
              case None =>
                DecodeResult.fail("Malformed organization field", c.history)
              case Some(org) => DecodeResult.ok(Option(org))
            }
        }
      }
    }

  implicit val decoder: DecodeJson[Conf] =
    DecodeJson { c =>
      for {
        s <- c.as(SimpleFields.decoder)
        orgDetailsOpt <- c.as(orgDetailsDecoder)
      } yield {
        Conf(
          orgDetailsOpt.getOrElse(OrganizationDetails.empty),
          s.versionOpt,
          s.homePageOpt,
          s.licenses,
          s.developers.map(_.map(_.get))
        )
      }
    }

  def load(path: Path): Either[String, Conf] = {

    // TODO catch errors and report them here
    val s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

    s.decodeEither(decoder)
  }

}
