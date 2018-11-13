package coursier.cli.publish.conf

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import argonaut._
import argonaut.Argonaut._
import coursier.core.Organization

final case class Conf(
  organization: OrganizationDetails,
  version: Option[String]
)

object Conf {

  implicit val decoder: DecodeJson[Conf] =
    DecodeJson { c =>

      val orgField = c.downField("organization").success
        .orElse(c.downField("organisation").success)
        .orElse(c.downField("org").success)

      val orgOpt = orgField match {
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

      val versionOpt = c.downField("version").success
        .orElse(c.downField("ver").success)
        .map(_.as[String].map(Option(_)))
        .getOrElse(DecodeResult.ok(None))

      for {
        o <- orgOpt
        v <- versionOpt
      } yield Conf(o.getOrElse(OrganizationDetails.empty), v)
    }

  def load(path: Path): Either[String, Conf] = {

    // TODO catch errors and report them here
    val s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

    s.decodeEither(decoder)
  }

}
