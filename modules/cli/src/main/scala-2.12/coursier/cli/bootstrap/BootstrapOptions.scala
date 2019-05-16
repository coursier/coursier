package coursier.cli.bootstrap

import caseapp.{Parser, Recurse}
import coursier.cli.options.SharedLaunchOptions

final case class BootstrapOptions(
  @Recurse
    nativeOptions: NativeBootstrapOptions = NativeBootstrapOptions(),
  @Recurse
    sharedLaunchOptions: SharedLaunchOptions = SharedLaunchOptions(),
  @Recurse
    options: BootstrapSpecificOptions = BootstrapSpecificOptions()
)

object BootstrapOptions {
  implicit val parser = Parser[BootstrapOptions]
  implicit val help = caseapp.core.help.Help[BootstrapOptions]
}
