package coursier.cli.search

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, OutputOptions, RepositoryOptions}

// format: off
final case class SearchOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)
// format: on

object SearchOptions {
  implicit val parser = caseapp.core.parser.Parser[SearchOptions]
  implicit val help   = caseapp.core.help.Help[SearchOptions]
}
