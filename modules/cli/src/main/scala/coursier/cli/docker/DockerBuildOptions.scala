package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import caseapp.Name
import caseapp.HelpMessage

final case class DockerBuildOptions(
  @Recurse
  sharedPullOptions: SharedDockerPullOptions = SharedDockerPullOptions(),
  @HelpMessage("Path to Dockerfile (default: Dockerfile under directory passed as docker context)")
  @Name("f")
  @Name("dockerfile")
  dockerFile: Option[String] = None
)

object DockerBuildOptions {
  implicit lazy val parser: Parser[DockerBuildOptions] = Parser.derive
  implicit lazy val help: Help[DockerBuildOptions]     = Help.derive
}
