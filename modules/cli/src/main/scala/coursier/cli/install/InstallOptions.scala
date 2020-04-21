package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions}

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
    envOptions: EnvOptions = EnvOptions(),

  addChannel: List[String] = Nil,

  @Short("f")
    force: Boolean = false

)

object InstallOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallOptions]
  implicit val help = caseapp.core.help.Help[InstallOptions]
}
