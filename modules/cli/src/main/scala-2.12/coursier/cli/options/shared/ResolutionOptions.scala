package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.ResolutionProcess

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

  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false

)

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help = caseapp.core.help.Help[ResolutionOptions]
}
