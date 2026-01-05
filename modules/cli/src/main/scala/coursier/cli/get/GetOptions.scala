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
  @HelpMessage("Whether the passed URL(s) correspond to archives. By default, this is inferred for each URL individually: if a URL contains '!', it is assumed to be an archive.")
    archive: Option[Boolean] = None,
  @HelpMessage("Archive cache location")
    archiveCache: Option[String] = None,
  @HelpMessage("HTTP headers to use as authentication (\"header: value\"), can be specified multiples times to add multiple headers")
    authHeader: List[String] = Nil,
  @HelpMessage("URL of reference artifact. If this file exists and is in cache, not-found errors for the main artifact are cached.")
    referenceUrl: Option[String] = None
)
// format: on

object GetOptions {
  implicit val parser: Parser[GetOptions]  = Parser.derive
  implicit lazy val help: Help[GetOptions] = Help.derive
}
