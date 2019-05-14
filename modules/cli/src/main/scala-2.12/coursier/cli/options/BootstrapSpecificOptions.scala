package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class BootstrapSpecificOptions(
  @Short("o")
    output: String = "bootstrap",
  @Short("f")
    force: Boolean = false,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Boolean = false,
  @Help("Include files in generated launcher even in non-standalone mode.")
  @Short("s")
    embedFiles: Boolean = true,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,
  @Help("Set Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,
  @Help("Generate native launcher")
  @Short("S")
    native: Boolean = false,
  @Help("Generate an assembly rather than a bootstrap jar")
  @Short("a")
    assembly: Boolean = false,
  @Help("Generate a Windows bat file along the bootstrap JAR (default: true on Windows, false else)")
    bat: Option[Boolean] = None,
  @Help("Add assembly rule")
  @Value("append:$path|append-pattern:$pattern|exclude:$path|exclude-pattern:$pattern")
  @Short("R")
    assemblyRule: List[String] = Nil,
  @Help("Add default rules to assembly rule list")
    defaultAssemblyRules: Boolean = true,
  @Help("Add preamble")
    preamble: Boolean = true,
  @Help("Ensure that the output jar is deterministic, set the instant of the added files to Jan 1st 1970")
    deterministic: Boolean = false,
  @Help("Use proguarded bootstrap")
    proguarded: Boolean = true,
  @Help("Have the bootstrap or assembly disable jar checking via a hard-coded Java property (default: true for bootstraps with resources, false else)")
    disableJarChecking: Option[Boolean] = None
)

object BootstrapSpecificOptions {
  implicit val parser = Parser[BootstrapSpecificOptions]
  implicit val help = caseapp.core.help.Help[BootstrapSpecificOptions]
}
