package coursier.cli.install

import caseapp.core.parser.Parser

final case class InstallPathOptions()

object InstallPathOptions {
  implicit val parser = Parser[InstallPathOptions]
  implicit val help = caseapp.core.help.Help[InstallPathOptions]
}
