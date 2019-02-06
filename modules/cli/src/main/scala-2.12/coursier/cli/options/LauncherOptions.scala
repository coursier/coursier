package coursier.cli.options

import caseapp.core.help.Help
import caseapp.{Hidden, Parser}

// format: off
final case class LauncherOptions(
  @Hidden
    completions: Option[String] = None
)
// format: on

object LauncherOptions {
  implicit val parser = Parser[LauncherOptions]
  implicit val help = Help[LauncherOptions]
}
