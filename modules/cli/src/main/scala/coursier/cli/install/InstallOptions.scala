package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class InstallOptions(

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  channel: List[String] = Nil,

  defaultChannels: Boolean = true,
  fileChannels: Boolean = true,

  defaultRepositories: Boolean = true,

  @Help("Name of the binary of the app to be installed")
    name: Option[String] = None,

  addChannel: List[String] = Nil
)

object InstallOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallOptions]
  implicit val help = caseapp.core.help.Help[InstallOptions]
}
