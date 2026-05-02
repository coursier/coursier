package coursier.cli.install

import caseapp._
import caseapp.Group
import coursier.cli.options.OptionGroup

// format: off
@HelpMessage(
  "List all currently installed applications.\n" +
  "\n" +
  "Example:\n" +
  "$ cs list\n"
)
final case class ListOptions(
  @Group(OptionGroup.install)
  @ExtraName("dir")
    installDir: Option[String] = None,
  @Group(OptionGroup.install)
  @HelpMessage("Show version of installed applications (can be disabled with --versions=false)")
    versions: Boolean = true,
)
// format: on

object ListOption {
  implicit lazy val parser: Parser[ListOptions] = Parser.derive
  implicit lazy val help: Help[ListOptions]     = Help.derive
}
