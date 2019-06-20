package coursier.cli.launch

import caseapp._
import coursier.cli.app.RawAppDescriptor
import coursier.cli.options.SharedLaunchOptions

@ArgsName("org:name:version|app-name[:version]*")
final case class LaunchOptions(

  @Recurse
    sharedOptions: SharedLaunchOptions = SharedLaunchOptions(),

  json: Boolean = false // move to SharedLaunchOptions? (and handle it from the other commands too)
) {
  def addApp(app: RawAppDescriptor): LaunchOptions =
    copy(
      sharedOptions = sharedOptions.addApp(app)
    )

  def app: RawAppDescriptor =
    sharedOptions.app
}

object LaunchOptions {
  implicit val parser = Parser[LaunchOptions]
  implicit val help = caseapp.core.help.Help[LaunchOptions]
}
