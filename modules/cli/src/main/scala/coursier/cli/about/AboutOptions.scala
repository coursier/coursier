package coursier.cli.about

import caseapp.{Help, HelpMessage}
import caseapp.core.parser.Parser

// format: off
@HelpMessage(
  "Print details about the current machine and coursier launcher.\n"
)
final case class AboutOptions()
// format: on

object AboutOptions {
  implicit lazy val parser: Parser[AboutOptions] = Parser.derive
  implicit lazy val help: Help[AboutOptions]     = Help.derive
}
