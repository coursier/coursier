package coursier.cli.setup

import caseapp.core.parser.Parser
import caseapp.{Group, Help, HelpMessage, Hidden, Name, Recurse}
import coursier.cli.install.{SharedChannelOptions, SharedInstallOptions}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{
  CacheOptions,
  EnvOptions,
  OptionGroup,
  OutputOptions,
  RepositoryOptions
}

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
  @Group(OptionGroup.setup)
    env: Boolean = false,
  @Group(OptionGroup.setup)
  @Hidden
    userHome: Option[String] = None,
  @Group(OptionGroup.setup)
  @Hidden
    banner: Option[Boolean] = None,
  @Name("y")
    yes: Option[Boolean] = None,
  @Group(OptionGroup.setup)
  @Hidden
    tryRevert: Boolean = false,
  @Group(OptionGroup.setup)
    apps: List[String] = Nil
) {
  // format: on
  def envOptions: EnvOptions =
    EnvOptions(env = env, userHome = userHome)
}

object SetupOptions {
  implicit val parser = Parser[SetupOptions]
  implicit val help   = Help[SetupOptions]
}
