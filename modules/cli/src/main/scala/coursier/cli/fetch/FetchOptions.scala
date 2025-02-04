package coursier.cli.fetch

import caseapp._
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.{ArtifactOptions, OptionGroup}
import coursier.cli.resolve.SharedResolveOptions
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@HelpMessage(
  "Transitively fetch the JARs of one or more dependencies or an application.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs fetch io.circe::circe-generic:0.12.3\n"
)
final case class FetchOptions(

  @Group(OptionGroup.fetch)
  @HelpMessage("Print java -cp compatible output")
  @ExtraName("p")
    classpath: Boolean = false,

  @Group(OptionGroup.fetch)
  @HelpMessage("Specify path for json output")
  @Hidden
  @ExtraName("j")
    jsonOutputFile: String = "",

  @Group(OptionGroup.fetch)
  @HelpMessage("Whether to use legacy report generation - might be buggy (defaults to false)")
  @Hidden
    legacyReportNoGuarantees: Option[Boolean] = None,


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
  implicit lazy val parser: Parser[FetchOptions] = Parser.derive
  implicit lazy val help: Help[FetchOptions]     = Help.derive
}
