package coursier.cli.fetch

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.ArtifactOptions
import coursier.cli.resolve.SharedResolveOptions
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version|app-name[:version]*")
final case class FetchOptions(

  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean = false,

  @Help("Specify path for json output")
  @Short("j")
    jsonOutputFile: String = "",


  @Recurse
    resolveOptions: SharedResolveOptions = SharedResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),

  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions()

) {
  // format: on

  def addApp(app: RawAppDescriptor): FetchOptions =
    copy(
      resolveOptions = resolveOptions.addApp(app),
      artifactOptions = artifactOptions.addApp(app)
    )
}

object FetchOptions {
  implicit val parser = Parser[FetchOptions]
  implicit val help   = caseapp.core.help.Help[FetchOptions]
}
