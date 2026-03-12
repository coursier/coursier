package coursier.cli.docker

import cats.data.ValidatedNel
import cats.syntax.all._

final case class DockerPullParams(
  sharedPullParams: SharedDockerPullParams,
  sharedVmSelectParams: SharedVmSelectParams
)

object DockerPullParams {
  def apply(options: DockerPullOptions): ValidatedNel[String, DockerPullParams] =
    (
      SharedDockerPullParams(options.sharedPullOptions),
      SharedVmSelectParams(options.sharedVmSelectOptions)
    ).mapN {
      (sharedPullParams, sharedVmSelectParams) =>
        DockerPullParams(
          sharedPullParams,
          sharedVmSelectParams
        )
    }
}
