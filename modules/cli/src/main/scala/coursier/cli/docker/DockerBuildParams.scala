package coursier.cli.docker

import cats.data.ValidatedNel
import cats.syntax.all._

final case class DockerBuildParams(
  sharedPullParams: SharedDockerPullParams,
  sharedVmSelectParams: SharedVmSelectParams,
  dockerFile: Option[os.Path]
)

object DockerBuildParams {
  def apply(options: DockerBuildOptions): ValidatedNel[String, DockerBuildParams] =
    (
      SharedDockerPullParams(options.sharedPullOptions),
      SharedVmSelectParams(options.sharedVmSelectOptions)
    ).mapN {
      (sharedPullParams, sharedVmSelectParams) =>
        DockerBuildParams(
          sharedPullParams,
          sharedVmSelectParams,
          options.dockerFile
            .filter(_.trim.nonEmpty)
            .map(os.Path(_, os.pwd))
        )
    }
}
