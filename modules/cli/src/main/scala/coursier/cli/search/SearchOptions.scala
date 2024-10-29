package coursier.cli.search

import caseapp._
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.{CacheOptions, OutputOptions, RepositoryOptions}

// format: off
@ArgsName("query*")
@HelpMessage(
  "Search application names from known channels.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs search scala\n" +
  "$ cs search fmt fix\n"
)
final case class SearchOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)
// format: on

object SearchOptions {
  implicit val parser                         = caseapp.core.parser.Parser[SearchOptions]
  implicit lazy val help: Help[SearchOptions] = Help.derive
}
