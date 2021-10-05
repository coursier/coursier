package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class SharedInstallOptions(

  @Group("Install")
  @Hidden
    graalvmHome: Option[String] = None,

  @Group("Install")
  @Hidden
    graalvmOption: List[String] = Nil,

  @Group("Install")
  @Hidden
    graalvmDefaultVersion: Option[String] = SharedInstallOptions.defaultGraalvmVersion,

  @Group("Install")
  @Short("dir")
    installDir: Option[String] = None,

  @Group("Install")
  @Hidden
  @Help("Platform for prebuilt binaries (e.g. \"x86_64-pc-linux\", \"x86_64-apple-darwin\", \"x86_64-pc-win32\")")
    installPlatform: Option[String] = None,

  @Group("Install")
  @Hidden
    installPreferPrebuilt: Boolean = true,

  @Group("Install")
  @Hidden
  @Help("Require prebuilt artifacts for native applications, don't try to build native executable ourselves")
    onlyPrebuilt: Boolean = false,

  @Group("Install")
  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  @Group("Install")
  @Hidden
    defaultRepositories: Boolean = true,

  @Group("Install")
  @Hidden
    proguarded: Option[Boolean] = None

)
// format: on

object SharedInstallOptions {
  def defaultGraalvmVersion: Option[String] =
    Some("19.3")
}
