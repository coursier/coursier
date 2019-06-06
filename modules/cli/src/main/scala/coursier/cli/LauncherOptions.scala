package coursier.cli

import caseapp.core.help.Help
import caseapp.{Hidden, Parser}

final case class LauncherOptions(
  @Hidden
    completions: Option[String] = None,

  @Hidden
    require: Option[String] = None
)

object LauncherOptions {
  implicit val parser = Parser[LauncherOptions]
  implicit val help = Help[LauncherOptions]
}
