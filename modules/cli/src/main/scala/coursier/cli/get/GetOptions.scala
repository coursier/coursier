package coursier.cli.get

import caseapp.core.help.Help
import caseapp.core.parser.Parser
import caseapp.{Name, Recurse}
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class GetOptions(
  @Recurse
    cache: CacheOptions,
  @Recurse
    output: OutputOptions,
  @Name("0")
    zero: Boolean = false,
  separator: Option[String] = None,
  force: Boolean = false,
  changing: Boolean = false
)

object GetOptions {
  implicit val parser = implicitly[Parser[GetOptions]]
  implicit val help = implicitly[Help[GetOptions]]
}
