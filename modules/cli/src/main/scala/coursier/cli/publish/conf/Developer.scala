package coursier.cli.publish.conf

import argonaut._

final case class Developer(
  id: String,
  name: String,
  url: String,
  email: Option[String] = None
) {
  def get: coursier.publish.Pom.Developer =
    coursier.publish.Pom.Developer(
      id,
      name,
      url,
      email
    )
}

object Developer {

  import argonaut.ArgonautShapeless._
  implicit val decoder = DecodeJson.of[Developer]

}
