package coursier.cli.options

import caseapp._

import scala.util.Properties

// format: off
final case class EnvOptions(

  @Group(OptionGroup.scripting)
    env: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
  @Name("disable")
    disableEnv: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
    windowsScript: Boolean = Properties.isWin,

  @Group(OptionGroup.scripting)
    setup: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
    userHome: Option[String] = None

)
// format: on
