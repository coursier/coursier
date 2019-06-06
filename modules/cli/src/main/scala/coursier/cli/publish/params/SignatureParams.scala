package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import coursier.cli.publish.options.SignatureOptions

final case class SignatureParams(
  gpg: Boolean,
  gpgKeyOpt: Option[String]
)

object SignatureParams {
  def apply(options: SignatureOptions): ValidatedNel[String, SignatureParams] = {
    // check here that the passed gpg key exists?
    Validated.validNel(
      SignatureParams(
        // TODO Adjust default value if --sonatype is passed
        options.gpg.getOrElse(options.gpgKey.nonEmpty),
        options.gpgKey
      )
    )
  }
}
