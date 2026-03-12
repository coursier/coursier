package coursier.cli

import caseapp.{Help, Hidden, Parser}

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
  implicit lazy val parser: Parser[LauncherOptions] = Parser.derive
  implicit lazy val help: Help[LauncherOptions]     = Help.derive
}
