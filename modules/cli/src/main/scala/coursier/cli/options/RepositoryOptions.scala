package coursier.cli.options

import caseapp._
import coursier.cli.install.SharedChannelOptions

// format: off
final case class RepositoryOptions(

  @Group(OptionGroup.repository)
  @HelpMessage("Repository - for multiple repositories, specify this option multiple times (e.g. -r central -r ivy2local -r sonatype:snapshots)")
  @ValueDescription("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|scala-integration|scala-nightlies|ivy:$pattern|jitpack|clojars|jcenter|apache:$repo")
  @ExtraName("r")
    repository: List[String] = Nil,

  @Group(OptionGroup.repository)
  @Hidden
  @HelpMessage("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,

  @Group(OptionGroup.repository)
  @Hidden
  @HelpMessage("Modify names in Maven repository paths for sbt plugins")
    sbtPluginHack: Boolean = true,

  @Group(OptionGroup.repository)
  @Hidden
  @HelpMessage("Drop module attributes starting with 'info.' - these are sometimes used by projects built with sbt")
    dropInfoAttr: Boolean = false,

  @Group(OptionGroup.repository)
  @Hidden
  @HelpMessage("Enable Gradle Module support (experimental)")
  @Name("enableModules")
    enableGradleModules: Boolean = false

)
// format: on

object RepositoryOptions {
  implicit lazy val parser: Parser[RepositoryOptions] = Parser.derive
  implicit lazy val help: Help[RepositoryOptions]     = Help.derive
}
