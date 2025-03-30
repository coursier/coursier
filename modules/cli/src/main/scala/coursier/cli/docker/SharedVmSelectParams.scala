package coursier.cli.docker

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all._

final case class SharedVmSelectParams(
  id: String
)

object SharedVmSelectParams {
  def apply(options: SharedVmSelectOptions): ValidatedNel[String, SharedVmSelectParams] =
    Validated.validNel(
      SharedVmSelectParams(
        options.vmId.map(_.trim).filter(_.nonEmpty).getOrElse("default")
      )
    )
}
