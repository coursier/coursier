package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class UpdateOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions(),

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  overrideRepositories: Boolean = false,

  @Short("f")
    force: Boolean = false

)

object UpdateOptions {
  implicit val parser = Parser[UpdateOptions]
  implicit val help = caseapp.core.help.Help[UpdateOptions]
}
