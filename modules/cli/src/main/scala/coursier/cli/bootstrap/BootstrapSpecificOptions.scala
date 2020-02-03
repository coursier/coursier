package coursier.cli.bootstrap

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.app.RawAppDescriptor

final case class BootstrapSpecificOptions(
  @Short("o")
    output: Option[String] = None,
  @Short("f")
    force: Boolean = false,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Option[Boolean] = None,
  @Help("Generate an hybrid assembly / standalone launcher")
    hybrid: Option[Boolean] = None,
  @Help("Include files in generated launcher even in non-standalone mode.")
    embedFiles: Boolean = true,
  @Help("Add Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,
  @Help("Generate an assembly rather than a bootstrap jar")
  @Short("a")
    assembly: Option[Boolean] = None,
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
) {
  def addApp(app: RawAppDescriptor, native: Boolean): BootstrapSpecificOptions = {
    val count = Seq(assembly.exists(identity), standalone.exists(identity), native).count(identity)
    copy(
      output = output.orElse(app.name),
      javaOpt = app.javaOptions ++ javaOpt,
      standalone = standalone.orElse(if (count == 0 && app.launcherType == "standalone") Some(true) else None),
      assembly = assembly.orElse(if (count == 0 && app.launcherType == "assembly") Some(true) else None)
    )
  }
}

object BootstrapSpecificOptions {
  implicit val parser = Parser[BootstrapSpecificOptions]
  implicit val help = caseapp.core.help.Help[BootstrapSpecificOptions]
}
