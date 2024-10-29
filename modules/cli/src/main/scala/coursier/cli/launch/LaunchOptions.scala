package coursier.cli.launch

import caseapp._
import coursier.cli.install.SharedChannelOptions
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.SharedLaunchOptions
import coursier.install.RawAppDescriptor
import coursier.cli.options.OptionGroup

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@HelpMessage(
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

  @Group(OptionGroup.launch)
    fork: Option[Boolean] = None,

  @Group(OptionGroup.launch)
  @Hidden
    fetchCacheIKnowWhatImDoing: Option[String] = None,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage("Launch child application via execve (replaces the coursier process)")
    execve: Option[Boolean] = None,

  @Group(OptionGroup.launch)
  @Hidden
    json: Boolean = false, // move to SharedLaunchOptions? (and handle it from the other commands too)

  @Group(OptionGroup.launch)
  @Hidden
    jep: Boolean = false,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage("When launching an app with a shared loader, launch it using a temporary hybrid launcher rather than a temporary standalone launcher")
    hybrid: Boolean = false,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage(
    "Launch app using a temporary bootstrap launcher with hard-coded URLs, that is lightweight and can be copied to other machines. " +
    "This can be useful if the app inspects its class path, and copies the launcher somewhere else."
  )
    useBootstrap: Boolean = false,

  @Group(OptionGroup.launch)
  @Hidden
  @ValueDescription("append:$path|append-pattern:$pattern|exclude:$path|exclude-pattern:$pattern")
  @ExtraName("R")
  @HelpMessage("Assembly rules to use to launch an app with a shared loader, if a hybrid launcher is being used (see --hybrid)")
    assemblyRule: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage("When launching an app with a shared loader and --hybrid is passed, whether to add default rules to assembly rule list")
    defaultAssemblyRules: Boolean = true,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage("When launching an app with a shared loader, generate launchers in the passed directory rather than a temporary one. This also disables automatic removal of the generated launcher.")
    workDir: Option[String] = None
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
  implicit val help   = Help[LaunchOptions]
}
