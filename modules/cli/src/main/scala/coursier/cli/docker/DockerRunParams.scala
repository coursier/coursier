package coursier.cli.docker

import cats.data.ValidatedNel
import coursier.exec.Execve
import io.github.alexarchambault.isterminal.IsTerminal

final case class DockerRunParams(
  sharedPullParams: SharedDockerPullParams,
  interactive: Boolean,
  useExec: Boolean
)

object DockerRunParams {
  def apply(options: DockerRunOptions): ValidatedNel[String, DockerRunParams] =
    SharedDockerPullParams(options.sharedPullOptions).map {
      sharedPullParams =>
        DockerRunParams(
          sharedPullParams,
          options.interactive.getOrElse(IsTerminal.isTerminal()),
          options.exec.getOrElse(Execve.available())
        )
    }
}
