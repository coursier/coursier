package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.{Configuration, ResolutionProcess}

final case class ResolutionOptions(

  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,

  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,

  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String] = Nil,

  @Help("Force property in POM files")
  @Value("name=value")
    forceProperty: List[String] = Nil,

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

  @Help("Default scala version")
  @Short("e")
    scalaVersion: String = scala.util.Properties.versionNumberString,

  @Help("Default sbt version (if --sbt-plugin options are passed)")
  @Value("sbt version (short version X.Y is enough - note that for sbt 1.x, this should be passed 1.0)")
    sbtVersion: String = "1.0",

  @Help("Add intransitive dependencies")
    intransitive: List[String] = Nil,

  @Help("Add sbt plugin dependencies")
    sbtPlugin: List[String] = Nil,

  @Help("Default configuration (default(compile) by default)")
  @Value("configuration")
  @Short("c")
    defaultConfiguration: String = "default(compile)",

  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false

) {

  def defaultConfiguration0 = Configuration(defaultConfiguration)

}

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help = caseapp.core.help.Help[ResolutionOptions]
}
