package coursier.cli.about

import caseapp.core.parser.Parser
import caseapp.{Help, HelpMessage}

// format: off
@HelpMessage(
  "Print details about the current machine and coursier launcher.\n"
)
final case class AboutOptions() derives Parser, Help
// format: on
