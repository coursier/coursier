package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, _}

final case class CompleteOptions(

  @Recurse
    cacheOptions: shared.CacheOptions = shared.CacheOptions(),

  @Recurse
    repositoryOptions: shared.RepositoryOptions = shared.RepositoryOptions(),

  @Recurse
    outputOptions: shared.OutputOptions = shared.OutputOptions(),

  @Help("Default scala version")
  @Short("e")
    scalaVersion: Option[String] = None

) {
  lazy val scalaBinaryVersion: Option[String] =
    scalaVersion
      .filter(_.nonEmpty)
      .map(coursier.complete.Complete.scalaBinaryVersion)
}
