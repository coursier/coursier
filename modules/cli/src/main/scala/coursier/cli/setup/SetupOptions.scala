package coursier.cli.setup

import caseapp.core.parser.Parser
import caseapp.{Name => Short, Recurse}
import coursier.cli.install.{SharedChannelOptions, SharedInstallOptions}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions}

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
  env: Boolean = false,
  userHome: Option[String] = None,
  banner: Option[Boolean] = None,
  @Short("y")
    yes: Option[Boolean] = None,
  tryRevert: Boolean = false,
  apps: List[String] = Nil
) {
  def envOptions: EnvOptions =
    EnvOptions(env = env, userHome = userHome)
}

object SetupOptions {
  implicit val parser = Parser[SetupOptions]
  implicit val help = caseapp.core.help.Help[SetupOptions]
}
