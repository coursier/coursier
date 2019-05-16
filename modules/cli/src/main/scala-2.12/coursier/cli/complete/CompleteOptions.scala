package coursier.cli.complete

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.shared.{CacheOptions, OutputOptions, RepositoryOptions}

final case class CompleteOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions(),

  @Help("Default scala version")
  @Short("e")
    scalaVersion: Option[String] = None

) {
  lazy val scalaBinaryVersion: Option[String] =
    scalaVersion
      .filter(_.nonEmpty)
      .map(coursier.complete.Complete.scalaBinaryVersion)
}
