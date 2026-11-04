package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser

// format: off
final case class DockerPullOptions(
  @Recurse
    sharedPullOptions: SharedDockerPullOptions = SharedDockerPullOptions(),
  @Recurse
    sharedVmSelectOptions: SharedVmSelectOptions = SharedVmSelectOptions()
) derives Parser, Help
// format: on
