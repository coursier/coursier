package coursier.cli.fetch

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.app.RawAppDescriptor
import coursier.cli.options.ArtifactOptions
import coursier.cli.resolve.ResolveOptions

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

) {
  def addApp(app: RawAppDescriptor): FetchOptions =
    copy(
      resolveOptions = resolveOptions.addApp(app),
      artifactOptions = artifactOptions.addApp(app)
    )
}

object FetchOptions {
  implicit val parser = Parser[FetchOptions]
  implicit val help = caseapp.core.help.Help[FetchOptions]
}
