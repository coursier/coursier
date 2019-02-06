package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.shared.ArtifactOptions

// format: off
final case class FetchOptions(

  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean = false,

  @Help("Specify path for json output")
  @Short("j")
    jsonOutputFile: String = "",


  @Recurse
    resolveOptions: ResolveOptions = ResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions()

)
// format: on

object FetchOptions {
  implicit val parser = Parser[FetchOptions]
  implicit val help = caseapp.core.help.Help[FetchOptions]
}
