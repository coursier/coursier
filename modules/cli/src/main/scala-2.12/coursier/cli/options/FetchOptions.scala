package coursier.cli.options

import caseapp.{ HelpMessage => Help, ExtraName => Short, _ }

final case class FetchOptions(
  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean = false,
  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

object FetchOptions {
  implicit val parser = Parser[FetchOptions]
  implicit val help = caseapp.core.help.Help[FetchOptions]
}
