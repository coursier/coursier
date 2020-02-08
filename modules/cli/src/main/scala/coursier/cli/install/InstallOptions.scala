package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class InstallOptions(

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions(),

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  @Recurse
    sharedChannelOptions: SharedChannelOptions = SharedChannelOptions(),

  addChannel: List[String] = Nil,

  @Short("f")
    force: Boolean = false

)

object InstallOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallOptions]
  implicit val help = caseapp.core.help.Help[InstallOptions]
}
