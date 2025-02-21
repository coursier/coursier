package coursier.cli.get

import caseapp.core.parser.Parser
import caseapp.{ArgsName, Help, HelpMessage, Name, Recurse}
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
  changing: Option[Boolean] = None,
  archive: Boolean = false,
  @HelpMessage("Archive cache location")
    archiveCache: Option[String] = None
)
// format: on

object GetOptions {
  implicit val parser: Parser[GetOptions]  = Parser.derive
  implicit lazy val help: Help[GetOptions] = Help.derive
}
