package coursier.cli.options

import caseapp._

// format: off
final case class EnvOptions(

  @Group("Scripting")
    env: Boolean = false,

  @Group("Scripting")
  @Hidden
  @Name("disable")
    disableEnv: Boolean = false,

  @Group("Scripting")
    setup: Boolean = false,

  @Group("Scripting")
  @Hidden
    userHome: Option[String] = None

)
// format: on
