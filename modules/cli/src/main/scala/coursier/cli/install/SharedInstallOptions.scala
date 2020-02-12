package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class SharedInstallOptions(

  graalvmHome: Option[String] = None,
  graalvmOption: List[String] = Nil,
  graalvmDefaultVersion: Option[String] = SharedInstallOptions.defaultGraalvmVersion,

  @Short("dir")
    installDir: Option[String] = None,

  @Help("Require prebuilt artifacts for native applications, don't try to build native executable ourselves")
    onlyPrebuilt: Boolean = false,

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  defaultRepositories: Boolean = true

)

object SharedInstallOptions {
  def defaultGraalvmVersion: Option[String] =
    Some("19.3")
}
