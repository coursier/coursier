package coursier.cli.bootstrap

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.install.RawAppDescriptor

// format: off
final case class BootstrapSpecificOptions(
  @Group("Bootstrap")
  @Short("o")
    output: Option[String] = None,
  @Group("Bootstrap")
  @Hidden
  @Short("f")
    force: Boolean = false,
  @Group("Bootstrap")
  @Hidden
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Option[Boolean] = None,
  @Group("Bootstrap")
  @Hidden
  @Help("Generate an hybrid assembly / standalone launcher")
    hybrid: Option[Boolean] = None,
  @Recurse
    graalvmOptions: GraalvmOptions = GraalvmOptions(),
  @Group("Bootstrap")
  @Hidden
  @Help("Include files in generated launcher even in non-standalone mode.")
    embedFiles: Boolean = true,
  @Group("Bootstrap")
  @Help("Add Java command-line options in the generated launcher.")
  @Value("option")
    javaOpt: List[String] = Nil,
  @Group("Bootstrap")
    jvmOptionFile: Option[String] = None,
  @Group("Bootstrap")
  @Hidden
  @Help("Generate an assembly rather than a bootstrap jar")
  @Short("a")
    assembly: Option[Boolean] = None,
  @Group("Bootstrap")
  @Hidden
  @Help("Generate a JAR with the classpath as manifest rather than a bootstrap jar")
    manifestJar: Option[Boolean] = None,
  @Group("Bootstrap")
  @Help("Generate a Windows bat file along the bootstrap JAR (default: true on Windows, false otherwise)")
    bat: Option[Boolean] = None,
  @Group("Bootstrap")
  @Hidden
  @Help("Add assembly rule")
  @Value("append:$path|append-pattern:$pattern|exclude:$path|exclude-pattern:$pattern")
  @Short("R")
    assemblyRule: List[String] = Nil,
  @Group("Bootstrap")
  @Hidden
  @Help("Add default rules to assembly rule list")
    defaultAssemblyRules: Boolean = true,
  @Group("Bootstrap")
  @Hidden
  @Help("Manifest to use as a start when creating a manifest for assemblies")
    baseManifest: Option[String] = None,
  @Group("Bootstrap")
  @Hidden
  @Help("Add preamble")
    preamble: Boolean = true,
  @Group("Bootstrap")
  @Hidden
  @Help("Ensure that the output jar is deterministic, set the instant of the added files to Jan 1st 1970")
    deterministic: Boolean = false,
  @Group("Bootstrap")
  @Hidden
  @Help("Use proguarded bootstrap")
    proguarded: Boolean = true,
  @Group("Bootstrap")
  @Hidden
  @Help("Have the bootstrap or assembly disable jar checking via a hard-coded Java property (default: true for bootstraps with resources, false else)")
    disableJarChecking: Option[Boolean] = None,
  @Group("Bootstrap")
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
      javaOpt = app.javaOptions ++ javaOpt,
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
      ),
      jvmOptionFile = jvmOptionFile.orElse(app.jvmOptionFile)
    )
  }
}

object BootstrapSpecificOptions {
  implicit val parser = Parser[BootstrapSpecificOptions]
  implicit val help   = caseapp.core.help.Help[BootstrapSpecificOptions]
}
