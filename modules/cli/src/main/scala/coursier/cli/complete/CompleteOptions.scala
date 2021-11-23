package coursier.cli.complete

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.{CacheOptions, OutputOptions, RepositoryOptions}

// format: off
@ArgsName("org[:name[:version]]")
@Help(
  "Auto-complete Maven coordinates.\n" +
  "\n" + 
  "Examples:\n" +
  "$ cs complete-dep com.type\n" +
  "$ cs complete-dep org.scala-lang:\n" +
  "$ cs complete-dep org.scala-lang:scala-compiler:2.13.\n"
)
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
  // format: on

  lazy val scalaBinaryVersion: Option[String] =
    scalaVersion
      .filter(_.nonEmpty)
      .map(coursier.complete.Complete.scalaBinaryVersion)
}

object CompleteOptions {
  implicit val parser = Parser[CompleteOptions]
  implicit val help   = caseapp.core.help.Help[CompleteOptions]
}
