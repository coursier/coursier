package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage, Parser}

// format: off
@HelpMessage(
  "List all currently installed applications.\n" +
  "\n" +
  "Example:\n" +
  "$ cs list\n"
)
final case class ListOptions(
  @Short("dir")
    installDir: Option[String] = None,
)
// format: on

object ListOption {
  implicit val parser = Parser[ListOptions]
  implicit val help   = caseapp.core.help.Help[ListOptions]
}
