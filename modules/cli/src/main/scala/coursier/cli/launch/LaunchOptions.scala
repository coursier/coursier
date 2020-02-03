package coursier.cli.launch

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.app.RawAppDescriptor
import coursier.cli.options.SharedLaunchOptions

@ArgsName("org:name:version|app-name[:version]*")
final case class LaunchOptions(

  @Recurse
    sharedOptions: SharedLaunchOptions = SharedLaunchOptions(),

  @Help("Add Java command-line options, when forking")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,

  fetchCacheIKnowWhatImDoing: Option[String] = None,

  json: Boolean = false, // move to SharedLaunchOptions? (and handle it from the other commands too)

  jep: Boolean = false
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
