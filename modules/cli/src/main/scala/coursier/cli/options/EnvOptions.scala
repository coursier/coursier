package coursier.cli.options

import caseapp.Name

// format: off
final case class EnvOptions(
  env: Boolean = false,
  @Name("disable")
    disableEnv: Boolean = false,
  setup: Boolean = false,
  userHome: Option[String] = None
)
// format: on
