package coursier.cli.complete

import caseapp._
import coursier.cli.options.{CacheOptions, OutputOptions, RepositoryOptions}

// format: off
@ArgsName("org[:name[:version]]")
@HelpMessage(
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

  @HelpMessage("Default scala version")
  @ExtraName("e")
    scalaVersion: Option[String] = None

) {
  // format: on

  lazy val scalaBinaryVersion: Option[String] =
    scalaVersion
      .filter(_.nonEmpty)
      .map(coursier.complete.Complete.scalaBinaryVersion)
}

object CompleteOptions {
  implicit lazy val parser: Parser[CompleteOptions] = Parser.derive
  implicit lazy val help: Help[CompleteOptions]     = Help.derive
}
