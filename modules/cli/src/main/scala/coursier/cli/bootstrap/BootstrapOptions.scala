package coursier.cli.bootstrap

import caseapp.{ArgsName, Group, Help, HelpMessage, Parser, Recurse}
import coursier.cli.options.OptionGroup
import coursier.cli.install.SharedChannelOptions
import coursier.cli.native.NativeLauncherOptions
import coursier.cli.options.SharedLaunchOptions
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@HelpMessage(
  "Create a binary launcher from a dependency or an application descriptor.\n" +
  "The generated launcher can then be used without cs being installed.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs bootstrap org.scalameta::scalafmt-cli:2.4.2 -o scalafmt\n" +
  "$ cs bootstrap scalafmt -o scalafmt\n"
)
final case class BootstrapOptions(
  @Recurse
    nativeOptions: NativeLauncherOptions = NativeLauncherOptions(),
  @Recurse
    sharedLaunchOptions: SharedLaunchOptions = SharedLaunchOptions(),
  @Group(OptionGroup.launch)
    jvmOptionFile: Option[String] = None,
  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),
  @Recurse
    options: BootstrapSpecificOptions = BootstrapSpecificOptions()
) {
  def addApp(app: RawAppDescriptor): BootstrapOptions =
    copy(
      jvmOptionFile = jvmOptionFile.orElse(app.jvmOptionFile),
      sharedLaunchOptions = sharedLaunchOptions.addApp(app),
      options = options.addApp(app, sharedLaunchOptions.resolveOptions.dependencyOptions.native)
    )
}
// format: on

object BootstrapOptions {
  implicit lazy val parser: Parser[BootstrapOptions] = Parser.derive
  implicit lazy val help: Help[BootstrapOptions]     = Help.derive
}
