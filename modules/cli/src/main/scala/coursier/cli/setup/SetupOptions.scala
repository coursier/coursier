package coursier.cli.setup

import caseapp.core.parser.Parser
import caseapp.{Name => Short, Recurse}
import coursier.cli.install.{SharedChannelOptions, SharedInstallOptions}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class SetupOptions(
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),
  @Recurse
    sharedChannelOptions: SharedChannelOptions = SharedChannelOptions(),
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions(),
  home: Option[String] = None,
  banner: Option[Boolean] = None,
  @Short("y")
    yes: Boolean = false,
  tryRevert: Boolean = false
)

object SetupOptions {
  implicit val parser = Parser[SetupOptions]
  implicit val help = caseapp.core.help.Help[SetupOptions]
}
