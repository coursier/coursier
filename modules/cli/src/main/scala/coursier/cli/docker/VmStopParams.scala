package coursier.cli.docker

import cats.data.ValidatedNel
import cats.syntax.all._

final case class VmStopParams(
  vmSelect: SharedVmSelectParams,
  fail: Boolean
)

object VmStopParams {
  def apply(options: VmStopOptions): ValidatedNel[String, VmStopParams] =
    SharedVmSelectParams(options.sharedVmSelectOptions).map {
      sharedVmSelect =>
        VmStopParams(
          sharedVmSelect,
          options.fail
        )
    }
}
