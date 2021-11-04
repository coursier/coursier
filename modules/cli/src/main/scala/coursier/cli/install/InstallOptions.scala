package coursier.cli.install

import caseapp._
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}

// format: off
@ArgsName("app-name[:version]*")
@HelpMessage(
  "Install an application from its descriptor.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs install scalafmt\n" +
  "$ cs install --channel io.get-coursier:apps-contrib proguard\n" +
  "$ cs install --contrib proguard\n"
)
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

  @Group("App channel")
  @HelpMessage("(deprecated)")
  @Hidden
    addChannel: List[String] = Nil,

  @Group("Install")
  @ExtraName("f")
    force: Boolean = false

)
// format: on

object InstallOptions {
  implicit val parser = Parser[InstallOptions]
  implicit val help   = Help[InstallOptions]
}
