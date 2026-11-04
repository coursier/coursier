package coursier.cli.docker

import caseapp.core.help.Help
import caseapp.core.parser.Parser
import caseapp.{HelpMessage, Name, Recurse}

// format: off
final case class DockerBuildOptions(
  @Recurse
    sharedPullOptions: SharedDockerPullOptions = SharedDockerPullOptions(),
  @Recurse
    sharedVmSelectOptions: SharedVmSelectOptions = SharedVmSelectOptions(),
  @HelpMessage("Path to Dockerfile (default: Dockerfile under directory passed as docker context)")
  @Name("f")
  @Name("dockerfile")
    dockerFile: Option[String] = None
) derives Parser, Help
// format: on
