package coursier.cli.install

import caseapp.{ExtraName => Short, Parser}

// format: off
final case class ListOptions(
  @Short("dir")
    installDir: Option[String] = None,
)
// format: on

object ListOption {
  implicit val parser = Parser[ListOptions]
  implicit val help   = caseapp.core.help.Help[ListOptions]
}
