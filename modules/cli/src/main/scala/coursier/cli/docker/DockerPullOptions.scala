package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser

final case class DockerPullOptions(
  @Recurse
  sharedPullOptions: SharedDockerPullOptions = SharedDockerPullOptions()
)

object DockerPullOptions {
  implicit lazy val parser: Parser[DockerPullOptions] = Parser.derive
  implicit lazy val help: Help[DockerPullOptions]     = Help.derive
}
