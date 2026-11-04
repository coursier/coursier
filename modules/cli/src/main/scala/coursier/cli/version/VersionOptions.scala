package coursier.cli.version

import caseapp._

@HelpMessage("Prints the coursier version")
final case class VersionOptions() derives Parser, Help
