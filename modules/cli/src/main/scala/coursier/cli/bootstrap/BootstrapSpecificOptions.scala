package coursier.cli.bootstrap

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.install.RawAppDescriptor
import coursier.cli.options.OptionGroup

// format: off
final case class BootstrapSpecificOptions(
  @Group(OptionGroup.bootstrap)
  @Short("o")
    output: Option[String] = None,
  @Group(OptionGroup.bootstrap)
  @Short("f")
    force: Boolean = false,
  @Group(OptionGroup.bootstrap)
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Option[Boolean] = None,
  @Group(OptionGroup.bootstrap)
  @Help("Generate an hybrid assembly / standalone launcher")
    hybrid: Option[Boolean] = None,
  @Recurse
    graalvmOptions: GraalvmOptions = GraalvmOptions(),
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Include files in generated launcher even in non-standalone mode.")
    embedFiles: Boolean = true,
  @Group(OptionGroup.bootstrap)
  @Help("Generate an assembly rather than a bootstrap jar")
  @Short("a")
    assembly: Option[Boolean] = None,
  @Group(OptionGroup.bootstrap)
  @Help("Generate a JAR with the classpath as manifest rather than a bootstrap jar")
    manifestJar: Option[Boolean] = None,
  @Group(OptionGroup.bootstrap)
  @Help("Generate a Windows bat file along the bootstrap JAR (default: true on Windows, false otherwise)")
    bat: Option[Boolean] = None,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Add assembly rule")
  @Value("append:$path|append-pattern:$pattern|exclude:$path|exclude-pattern:$pattern")
  @Short("R")
    assemblyRule: List[String] = Nil,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Add default rules to assembly rule list")
    defaultAssemblyRules: Boolean = true,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Manifest to use as a start when creating a manifest for assemblies")
    baseManifest: Option[String] = None,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Add preamble")
    preamble: Boolean = true,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Ensure that the output jar is deterministic, set the instant of the added files to Jan 1st 1970")
    deterministic: Boolean = false,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Use proguarded bootstrap")
    proguarded: Boolean = true,
  @Group(OptionGroup.bootstrap)
  @Hidden
  @Help("Have the bootstrap or assembly disable jar checking via a hard-coded Java property (default: true for bootstraps with resources, false else)")
    disableJarChecking: Option[Boolean] = None,
  @Group(OptionGroup.bootstrap)
  @Hidden
    jvmIndex: Option[String] = None
) {
  // format: on

  def addApp(app: RawAppDescriptor, native: Boolean): BootstrapSpecificOptions = {
    import graalvmOptions.{copy => _, _}
    val count = Seq(
      assembly.exists(identity),
      standalone.exists(identity),
      native,
      nativeImage.exists(identity) ||
      graalvmVersion
        .map(_.trim)
        .filter(_.nonEmpty)
        .filter(_ => !nativeImage.contains(false))
        .nonEmpty ||
      (!nativeImage.contains(false) &&
      (graalvmJvmOption.filter(_.nonEmpty).nonEmpty ||
      graalvmOption.filter(_.nonEmpty).nonEmpty))
    ).count(identity)
    copy(
      output = output.orElse(app.name),
      standalone = standalone
        .orElse(if (count == 0 && app.launcherType == "standalone") Some(true) else None),
      assembly = assembly
        .orElse(if (count == 0 && app.launcherType == "assembly") Some(true) else None),
      graalvmOptions = graalvmOptions.copy(
        nativeImage = nativeImage
          .orElse {
            if (count == 0 && app.launcherType == "graalvm-native-image") Some(true)
            else None
          }
      )
    )
  }
}

object BootstrapSpecificOptions {
  implicit val parser = Parser[BootstrapSpecificOptions]
  implicit val help   = caseapp.core.help.Help[BootstrapSpecificOptions]
}
