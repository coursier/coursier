package coursier.cli.config

import caseapp._

// format: off
final case class ConfigOptions(
  @Group("Config")
  @HelpMessage("Dump config DB as JSON")
  @Hidden
    dump: Boolean = false,
  @Group("Config")
  @HelpMessage("If the entry is a password, print the password value rather than how to get the password")
    password: Boolean = false,
  @Group("Config")
  @HelpMessage("If the entry is a password, save the password value rather than how to get the password")
    passwordValue: Boolean = false,
  @Group("Config")
  @HelpMessage("Remove an entry from config")
    unset: Boolean = false,
  @Group("Config")
  @HelpMessage("Config file path")
    configFile: Option[String] = None
)
// format: on

object ConfigOptions {
  implicit lazy val parser: Parser[ConfigOptions] = Parser.derive
  implicit lazy val help: Help[ConfigOptions]     = Help.derive
}
