package coursier.cli.setup

import caseapp.core.parser.Parser
import caseapp.{Name, Recurse}
import coursier.cli.install.{SharedChannelOptions, SharedInstallOptions}
import coursier.cli.jvm.SharedJavaOptions

final case class SetupOptions(
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),
  @Recurse
    sharedChannelOptions: SharedChannelOptions = SharedChannelOptions(),
  home: Option[String] = None
)

object SetupOptions {
  implicit val parser = Parser[SetupOptions]
  implicit val help = caseapp.core.help.Help[SetupOptions]
}
