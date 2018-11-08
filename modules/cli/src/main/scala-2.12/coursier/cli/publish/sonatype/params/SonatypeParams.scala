package coursier.cli.publish.sonatype.params

import cats.data.{Validated, ValidatedNel}
import coursier.cli.publish.sonatype.options.SonatypeOptions
import coursier.core.Authentication

final case class SonatypeParams(
  base: String,
  authentication: Option[Authentication]
)

object SonatypeParams {
  def apply(options: SonatypeOptions): ValidatedNel[String, SonatypeParams] = {

    val authV = (options.user, options.password) match {
      case (None, None) =>
        (sys.env.get("SONATYPE_USERNAME"), sys.env.get("SONATYPE_PASSWORD")) match {
          case (Some(u), Some(p)) =>
            Validated.validNel(Some(Authentication(u, p)))
          case _ =>
            Validated.validNel(None)
        }
      case (Some(u), Some(p)) =>
        Validated.validNel(Some(Authentication(u, p)))
      case (Some(_), None) =>
        Validated.invalidNel("User specified, but no password passed")
      case (None, Some(_)) =>
        Validated.invalidNel("Password specified, but no user passed")
    }

    authV.map { auth =>
      SonatypeParams(
        options.base.getOrElse("https://oss.sonatype.org/service/local"),
        auth
      )
    }
  }
}
