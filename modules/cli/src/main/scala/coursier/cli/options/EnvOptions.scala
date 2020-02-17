package coursier.cli.options

import caseapp.Name

final case class EnvOptions(
  env: Boolean = false,
  @Name("disable")
    disableEnv: Boolean = false,
  setup: Boolean = false,
  userHome: Option[String] = None
)
