package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class RepositoryOptions(

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  @Help("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,

  @Help("Modify names in Maven repository paths for SBT plugins")
    sbtPluginHack: Boolean = true,

  @Help("Drop module attributes starting with 'info.' - these are sometimes used by projects built with SBT")
    dropInfoAttr: Boolean = false

)

object RepositoryOptions {
  implicit val parser = Parser[RepositoryOptions]
  implicit val help = caseapp.core.help.Help[RepositoryOptions]
}
