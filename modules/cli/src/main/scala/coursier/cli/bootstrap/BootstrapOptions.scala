package coursier.cli.bootstrap

import caseapp.{ArgsName, Parser, Recurse}
import coursier.cli.app.RawAppDescriptor
import coursier.cli.native.NativeLauncherOptions
import coursier.cli.options.SharedLaunchOptions

@ArgsName("org:name:version|app-name[:version]*")
final case class BootstrapOptions(
  @Recurse
    nativeOptions: NativeLauncherOptions = NativeLauncherOptions(),
  @Recurse
    sharedLaunchOptions: SharedLaunchOptions = SharedLaunchOptions(),
  @Recurse
    options: BootstrapSpecificOptions = BootstrapSpecificOptions()
) {
  def addApp(app: RawAppDescriptor): BootstrapOptions =
    copy(
      sharedLaunchOptions = sharedLaunchOptions.addApp(app),
      options = options.addApp(app, sharedLaunchOptions.resolveOptions.dependencyOptions.native)
    )
}

object BootstrapOptions {
  implicit val parser = Parser[BootstrapOptions]
  implicit val help = caseapp.core.help.Help[BootstrapOptions]
}
