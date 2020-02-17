package coursier.cli.launch

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.SharedLaunchOptions
import coursier.install.RawAppDescriptor

@ArgsName("org:name:version|app-name[:version]*")
final case class LaunchOptions(

  @Recurse
    sharedOptions: SharedLaunchOptions = SharedLaunchOptions(),

  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),

  @Help("Add Java command-line options, when forking")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,

  fetchCacheIKnowWhatImDoing: Option[String] = None,

  @Help("Launch child application via execve (replaces the coursier process)")
    execve: Option[Boolean] = None,

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
