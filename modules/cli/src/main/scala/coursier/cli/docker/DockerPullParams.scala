package coursier.cli.docker

import cats.data.ValidatedNel

final case class DockerPullParams(
  sharedPullParams: SharedDockerPullParams
)

object DockerPullParams {
  def apply(options: DockerPullOptions): ValidatedNel[String, DockerPullParams] =
    SharedDockerPullParams(options.sharedPullOptions).map {
      sharedPullParams =>
        DockerPullParams(
          sharedPullParams
        )
    }
}
