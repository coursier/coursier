package coursier.cli.docker

import cats.data.ValidatedNel
import cats.syntax.all._
import coursier.exec.Execve
import io.github.alexarchambault.isterminal.IsTerminal

final case class DockerRunParams(
  sharedPullParams: SharedDockerPullParams,
  sharedVmSelectParams: SharedVmSelectParams,
  interactive: Boolean,
  useExec: Boolean
)

object DockerRunParams {
  def apply(options: DockerRunOptions): ValidatedNel[String, DockerRunParams] =
    (
      SharedDockerPullParams(options.sharedPullOptions),
      SharedVmSelectParams(options.sharedVmSelectOptions)
    ).mapN {
      (sharedPullParams, sharedVmSelectParams) =>
        DockerRunParams(
          sharedPullParams,
          sharedVmSelectParams,
          options.interactive.getOrElse(IsTerminal.isTerminal()),
          options.exec.getOrElse(Execve.available())
        )
    }
}
