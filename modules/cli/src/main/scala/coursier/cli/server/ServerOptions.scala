package coursier.cli.server

import caseapp._
import coursier.cli.options.{CacheOptions, OutputOptions}
import coursier.cli.util.ArgParsers.*
import scala.cli.config.PasswordOption

// format: off
@HelpMessage("Start a cache server")
final case class ServerOptions(
  @Recurse
    cache: CacheOptions = CacheOptions(),
  @Recurse
    output: OutputOptions = OutputOptions(),
  @HelpMessage("Host to bind to")
    host: String = "localhost",
  @HelpMessage("Port to listen on")
    port: Int = 9090,
  @HelpMessage("HTTP basic auth user")
  @ValueDescription(
    "value:… or env:ENV_VAR_NAME or file:/path/to/file or command:simple-command or command:[\"json\", \"array\"]"
  )
    user: Option[PasswordOption] = None,
  @HelpMessage("HTTP basic auth password")
  @ValueDescription(
    "value:… or env:ENV_VAR_NAME or file:/path/to/file or command:simple-command or command:[\"json\", \"array\"]"
  )
    password: Option[PasswordOption] = None,
  @HelpMessage("Disable authentication")
    noAuth: Boolean = false
)
// format: on

object ServerOptions {
  implicit lazy val parser: Parser[ServerOptions] = Parser.derive
  implicit lazy val help: Help[ServerOptions]     = Help.derive
}
