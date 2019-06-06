package coursier.cli.publish.conf

import argonaut._
import argonaut.Argonaut._
import coursier.core.Organization

final case class OrganizationDetails(
  organization: Option[Organization],
  url: Option[String]
)

object OrganizationDetails {

  val empty = OrganizationDetails(None, None)

  implicit val decoder: DecodeJson[OrganizationDetails] =
    DecodeJson { c =>

      if (c.focus.isObject) {

        val nameOpt = c.field("name").success
          .orElse(c.field("value").success)
          .map(_.as[String].map(s => Option(Organization(s))))
          .getOrElse(DecodeResult.ok(None))

        val urlOpt = c.field("url").success
          .map(_.as[String].map(Option(_)))
          .getOrElse(DecodeResult.ok(None))

        for {
          n <- nameOpt
          u <- urlOpt
        } yield OrganizationDetails(n, u)
      } else
        DecodeResult.fail("Organization: not an object", c.history)
    }

}
