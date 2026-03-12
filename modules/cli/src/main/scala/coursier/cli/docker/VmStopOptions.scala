package coursier.cli.docker

import caseapp.Recurse
import caseapp.core.help.Help
import caseapp.core.parser.Parser

// format: off
final case class VmStopOptions(
  @Recurse
    sharedVmSelectOptions: SharedVmSelectOptions = SharedVmSelectOptions(),
  fail: Boolean = false
)
// format: on

object VmStopOptions {
  implicit lazy val parser: Parser[VmStopOptions] = Parser.derive
  implicit lazy val help: Help[VmStopOptions]     = Help.derive
}
