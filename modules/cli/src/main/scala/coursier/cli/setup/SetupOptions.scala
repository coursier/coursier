package coursier.cli.setup

import caseapp.core.parser.Parser
import caseapp.{HelpMessage, Name => Short, Recurse}
import coursier.cli.install.{SharedChannelOptions, SharedInstallOptions}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}

// format: off
@HelpMessage(
  "Setup a machine for Scala development.\n" +
  "Install Coursier itself and standard Scala tooling.\n" +
  "Also install a JVM if necessary.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs setup\n" +
  "$ cs setup --jvm 11 --apps bloop,scalafix\n"
)
final case class SetupOptions(
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),
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
  // format: on
  def envOptions: EnvOptions =
    EnvOptions(env = env, userHome = userHome)
}

object SetupOptions {
  implicit val parser = Parser[SetupOptions]
  implicit val help   = caseapp.core.help.Help[SetupOptions]
}
