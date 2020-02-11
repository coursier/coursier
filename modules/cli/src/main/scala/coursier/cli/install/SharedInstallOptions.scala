package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class SharedInstallOptions(

  graalvmHome: Option[String] = None,
  graalvmOption: List[String] = Nil,

  dir: Option[String] = None,

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  defaultRepositories: Boolean = true,


)
