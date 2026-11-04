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
) derives Parser, Help
// format: on
