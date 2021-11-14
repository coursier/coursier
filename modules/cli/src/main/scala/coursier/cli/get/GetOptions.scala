package coursier.cli.get

import caseapp.core.help.Help
import caseapp.core.parser.Parser
import caseapp.{ArgsName, HelpMessage, Name, Recurse}
import coursier.cli.options.{CacheOptions, OutputOptions}

// format: off
@ArgsName("url*")
@HelpMessage("Download and cache a file from its URL.")
final case class GetOptions(
  @Recurse
    cache: CacheOptions,
  @Recurse
    output: OutputOptions,
  @Name("0")
    zero: Boolean = false,
  separator: Option[String] = None,
  force: Boolean = false,
  changing: Boolean = false,
  archive: Boolean = false
)
// format: on

object GetOptions {
  implicit val parser = implicitly[Parser[GetOptions]]
  implicit val help   = implicitly[Help[GetOptions]]
}
