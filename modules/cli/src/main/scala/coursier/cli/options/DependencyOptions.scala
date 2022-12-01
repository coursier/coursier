package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class DependencyOptions(

  @Group(OptionGroup.dependency)
  @Hidden
  @Help("Exclude module")
  @Value("organization:name")
  @Short("E")
    exclude: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @Hidden
  @Short("x")
  @Help("Path to the local exclusion file. " +
    "Syntax: <org:name>--<org:name>. `--` means minus. Example file content:\n\t" +
    "\tcom.twitter.penguin:korean-text--com.twitter:util-tunable-internal_2.11\n\t" +
    "\torg.apache.commons:commons-math--com.twitter.search:core-query-nodes\n\t" +
    "Behavior: If root module A excludes module X, but root module B requires X, module X will still be fetched."
  )
    localExcludeFile: String = "",

  @Group(OptionGroup.dependency)
  @Hidden
  @Help("If --sbt-plugin options are passed: default sbt version  (short version X.Y is enough - note that for sbt 1.x, this should be passed 1.0)")
  @Value("sbt version")
    sbtVersion: String = "1.0",

  @Group(OptionGroup.dependency)
  @Hidden
  @Help("Add intransitive dependencies")
    intransitive: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @Help("Add sbt plugin dependencies")
    sbtPlugin: List[String] = Nil,

  @Group(OptionGroup.dependency)
  @Help("Enable Scala.js")
    scalaJs: Boolean = false,

  @Group(OptionGroup.dependency)
  @Help("Enable scala-native")
  @Short("S")
    native: Boolean = false,

  @Group(OptionGroup.dependency)
  @Help("Path to file with dependencies. " +
    "Dependencies should be separated with newline character")
    dependencyFiles: List[String] = Nil

)
// format: on

object DependencyOptions {
  implicit val parser = Parser[DependencyOptions]
  implicit val help   = caseapp.core.help.Help[DependencyOptions]
}
