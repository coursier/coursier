package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class DependencyOptions(

  @Help("Exclude module")
  @Value("organization:name")
  @Short("E")
  @Help("Global level exclude")
    exclude: List[String] = Nil,

  @Short("x")
  @Help("Path to the local exclusion file. " +
    "Syntax: <org:name>--<org:name>. `--` means minus. Example file content:\n\t" +
    "\tcom.twitter.penguin:korean-text--com.twitter:util-tunable-internal_2.11\n\t" +
    "\torg.apache.commons:commons-math--com.twitter.search:core-query-nodes\n\t" +
    "Behavior: If root module A excludes module X, but root module B requires X, module X will still be fetched."
  )
    localExcludeFile: String = "",

  @Help("If --sbt-plugin options are passed: default sbt version  (short version X.Y is enough - note that for sbt 1.x, this should be passed 1.0)")
  @Value("sbt version")
    sbtVersion: String = "1.0",

  @Help("Add intransitive dependencies")
    intransitive: List[String] = Nil,

  @Help("Add sbt plugin dependencies")
    sbtPlugin: List[String] = Nil,

  scalaJs: Boolean = false,

  @Help("Enable scala-native")
  @Short("S")
    native: Boolean = false

)

object DependencyOptions {
  implicit val parser = Parser[DependencyOptions]
  implicit val help = caseapp.core.help.Help[DependencyOptions]
}
