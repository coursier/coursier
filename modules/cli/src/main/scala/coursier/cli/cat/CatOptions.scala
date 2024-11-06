package coursier.cli.cat

import caseapp.core.parser.Parser
import caseapp.{ArgsName, Help, HelpMessage, Recurse}
import coursier.cli.options.{CacheOptions, OutputOptions}

// format: off
@ArgsName("url")
@HelpMessage("Download and cache a file from its URL, then print it in the console")
final case class CatOptions(
  @Recurse
    cache: CacheOptions,
  @Recurse
    output: OutputOptions,
  changing: Option[Boolean] = None
)
// format: on

object CatOptions {
  implicit val parser: Parser[CatOptions]  = Parser.derive
  implicit lazy val help: Help[CatOptions] = Help.derive
}
