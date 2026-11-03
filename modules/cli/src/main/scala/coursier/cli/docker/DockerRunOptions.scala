package coursier.cli.docker

import caseapp.{Name, Recurse}
import caseapp.core.help.Help
import caseapp.core.parser.Parser

// format: off
final case class DockerRunOptions(
  @Recurse
    sharedPullOptions: SharedDockerPullOptions = SharedDockerPullOptions(),
  @Recurse
    sharedVmSelectOptions: SharedVmSelectOptions = SharedVmSelectOptions(),
  @Name("i")
    interactive: Option[Boolean] = None,
  exec: Option[Boolean] = None
) derives Parser, Help
// format: on
