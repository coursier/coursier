package coursier.cli.options

import caseapp._

// format: off
final case class DependencyOptions(

  @Group(OptionGroup.dependency)
  @Hidden
  @HelpMessage("Exclude module")
  @ValueDescription("organization:name")
  @ExtraName("E")
    exclude: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @Hidden
  @ExtraName("x")
  @HelpMessage("Path to the local exclusion file. " +
    "Syntax: <org:name>--<org:name>. `--` means minus. Example file content:\n\t" +
    "\tcom.twitter.penguin:korean-text--com.twitter:util-tunable-internal_2.11\n\t" +
    "\torg.apache.commons:commons-math--com.twitter.search:core-query-nodes\n\t" +
    "Behavior: If root module A excludes module X, but root module B requires X, module X will still be fetched."
  )
    localExcludeFile: String = "",

  @Group(OptionGroup.dependency)
  @Hidden
  @HelpMessage("If --sbt-plugin options are passed: default sbt version  (short version X.Y is enough - note that for sbt 1.x, this should be passed 1.0)")
  @ValueDescription("sbt version")
    sbtVersion: String = "1.0",

  @Group(OptionGroup.dependency)
  @Hidden
  @HelpMessage("Add intransitive dependencies")
    intransitive: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @HelpMessage("Add sbt plugin dependencies")
    sbtPlugin: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @HelpMessage("Enable Scala.js")
    scalaJs: Boolean = false,

  @Group(OptionGroup.dependency)
  @HelpMessage("Enable scala-native")
  @ExtraName("S")
    native: Boolean = false,

  @Group(OptionGroup.dependency)
  @HelpMessage("Path to file with dependencies. " +
    "Dependencies should be separated with newline character")
    dependencyFile: List[String] = Nil

)
// format: on

object DependencyOptions {
  implicit val parser = Parser[DependencyOptions]
  implicit val help   = Help[DependencyOptions]
}
