package coursier.cli.options

import caseapp._

import scala.util.Properties

// format: off
final case class EnvOptions(

  @Group(OptionGroup.scripting)
  @HelpMessage("Prints out a script that can be used to setup the env")
    env: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
  @Name("disable")
    disableEnv: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
    windowsScript: Option[Boolean] = None,

  @Group(OptionGroup.scripting)
  @Hidden
    windowsPosixScript: Option[Boolean] = None,

  @Group(OptionGroup.scripting)
  @HelpMessage("Sets the default JVM to be used")
    setup: Boolean = false,

  @Group(OptionGroup.scripting)
  @Hidden
    userHome: Option[String] = None

)
// format: on
