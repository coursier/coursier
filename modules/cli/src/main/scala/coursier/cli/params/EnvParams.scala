package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.EnvOptions

final case class EnvParams(
  env: Boolean
)

object EnvParams {
  def apply(options: EnvOptions): ValidatedNel[String, EnvParams] =
    Validated.validNel(
      EnvParams(
        options.env
      )
    )
}
