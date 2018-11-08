package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import coursier.cli.publish.options.SignatureOptions

final case class SignatureParams(
  gpgKeyOpt: Option[String]
)

object SignatureParams {
  def apply(options: SignatureOptions): ValidatedNel[String, SignatureParams] = {
    // check here that the passed gpg key exists?
    Validated.validNel(
      SignatureParams(
        options.gpg
      )
    )
  }
}
