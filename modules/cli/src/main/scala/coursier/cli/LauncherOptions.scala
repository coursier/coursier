package coursier.cli

import caseapp.core.help.Help
import caseapp.{Hidden, Parser}

// format: off
final case class LauncherOptions(
  @Hidden
  version: Boolean = false,
  @Hidden
    completions: Option[String] = None,

  @Hidden
    require: Option[String] = None
)
// format: on

object LauncherOptions {
  implicit val parser = Parser[LauncherOptions]
  implicit val help   = Help[LauncherOptions]
}
