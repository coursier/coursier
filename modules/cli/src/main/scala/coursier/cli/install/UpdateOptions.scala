package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.jvm.SharedJavaOptions
import coursier.cli.options.{CacheOptions, OutputOptions, RepositoryOptions}

// format: off
final case class UpdateOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions(),

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  overrideRepositories: Boolean = false,

  @Short("f")
    force: Boolean = false

)
// format: on

object UpdateOptions {
  implicit val parser = Parser[UpdateOptions]
  implicit val help   = caseapp.core.help.Help[UpdateOptions]
}
