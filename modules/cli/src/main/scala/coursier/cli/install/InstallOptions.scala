package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}

// format: off
final case class InstallOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions(),

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  @Recurse
    sharedChannelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    envOptions: EnvOptions = EnvOptions(),

  @Help("(deprecated)")
  addChannel: List[String] = Nil,

  @Short("f")
    force: Boolean = false

)
// format: on

object InstallOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallOptions]
  implicit val help   = caseapp.core.help.Help[InstallOptions]
}
