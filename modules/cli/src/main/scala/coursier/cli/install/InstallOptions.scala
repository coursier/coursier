package coursier.cli.install

import caseapp.{HelpMessage => Help, _}

final case class InstallOptions(

  @Recurse
    appOptions: InstallAppOptions = InstallAppOptions(),

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  channel: List[String] = Nil,

  defaultChannels: Boolean = true,

  defaultRepositories: Boolean = true,

  @Help("Name of the binary of the app to be installed")
    name: Option[String] = None
)

object InstallOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallOptions]
  implicit val help = caseapp.core.help.Help[InstallOptions]
}
