package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser
import coursier.cli.options.{CacheOptions, OutputOptions}

// format: off
final case class SharedDockerPullOptions(
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions(),
  authRegistry: Option[String] = None,
  os: Option[String] = None,
  cpu: Option[String] = None,
  cpuVariant: Option[String] = None
)
// format: on

object SharedDockerPullOptions {
  implicit lazy val parser: Parser[SharedDockerPullOptions] = Parser.derive
  implicit lazy val help: Help[SharedDockerPullOptions]     = Help.derive
}
