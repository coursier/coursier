package coursier.cli.launch

import caseapp._
import coursier.cli.options.shared.SharedLaunchOptions

final case class LaunchOptions(

  @Recurse
    sharedOptions: SharedLaunchOptions = SharedLaunchOptions()
)

object LaunchOptions {
  implicit val parser = Parser[LaunchOptions]
  implicit val help = caseapp.core.help.Help[LaunchOptions]
}
