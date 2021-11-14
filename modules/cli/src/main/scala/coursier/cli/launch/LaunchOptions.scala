package coursier.cli.launch

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.install.SharedChannelOptions
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.SharedLaunchOptions
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@Help(
  "Launch an application from a dependency or an application descriptor.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs launch org.scalameta:scalafmt-cli:2.4.2 -- --version\n" +
  "$ cs scalafmt -- --version\n"
)
final case class LaunchOptions(

  @Recurse
    sharedOptions: SharedLaunchOptions = SharedLaunchOptions(),

  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),

  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Help("Add Java command-line options, when forking")
  @Value("option")
    javaOpt: List[String] = Nil,

  fetchCacheIKnowWhatImDoing: Option[String] = None,

  @Help("Launch child application via execve (replaces the coursier process)")
    execve: Option[Boolean] = None,

  json: Boolean = false, // move to SharedLaunchOptions? (and handle it from the other commands too)

  jep: Boolean = false
) {
  // format: on

  def addApp(app: RawAppDescriptor): LaunchOptions =
    copy(
      sharedOptions = sharedOptions.addApp(app)
    )

  def app: RawAppDescriptor =
    sharedOptions.app
}

object LaunchOptions {
  implicit val parser = Parser[LaunchOptions]
  implicit val help   = caseapp.core.help.Help[LaunchOptions]
}
