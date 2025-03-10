package coursier.cli.docker

import cats.data.ValidatedNel

final case class DockerBuildParams(
  sharedPullParams: SharedDockerPullParams,
  dockerFile: Option[os.Path]
)

object DockerBuildParams {
  def apply(options: DockerBuildOptions): ValidatedNel[String, DockerBuildParams] =
    SharedDockerPullParams(options.sharedPullOptions).map {
      sharedPullParams =>
        DockerBuildParams(
          sharedPullParams,
          options.dockerFile
            .filter(_.trim.nonEmpty)
            .map(os.Path(_, os.pwd))
        )
    }
}
