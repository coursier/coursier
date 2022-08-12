package coursier.cli.version

import caseapp._

@HelpMessage("Prints the coursier version")
final case class VersionOptions()

object VersionOptions {
  implicit lazy val parser: Parser[VersionOptions] = Parser.derive
  implicit lazy val help: Help[VersionOptions]     = Help.derive
}
