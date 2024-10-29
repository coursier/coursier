package coursier.cli.resolve

import caseapp._
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.{
  DependencyOptions,
  OptionGroup,
  OutputOptions,
  RepositoryOptions,
  ResolutionOptions
}
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@HelpMessage(
  "Resolve and print the transitive dependencies of one or more dependencies or an application.\n" +
  "Print the maven coordinates, does not download the artifacts.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs resolve org.http4s:http4s-dsl_2.12:0.18.21\n" +
  "$ cs resolve --tree org.http4s:http4s-dsl_2.12:0.18.21"
)
final case class ResolveOptions(

  @Group(OptionGroup.resolution)
  @HelpMessage("Print the duration of each iteration of the resolution (if negative, doesn't print per iteration benchmark -> less overhead)")
  @Hidden
  @ExtraName("B")
  @ValueDescription("# warm-up resolutions")
    benchmark: Int = 0,

  @Group(OptionGroup.resolution)
  @Hidden
    benchmarkCache: Boolean = false,

  @Group(OptionGroup.resolution)
  @HelpMessage("Print dependencies as a tree")
  @ExtraName("t")
    tree: Boolean = false,
  @Group(OptionGroup.resolution)
  @HelpMessage("Print dependencies as a reversed tree (dependees as children)")
  @ExtraName("T")
    reverseTree: Boolean = false,
  @Group(OptionGroup.resolution)
  @HelpMessage("Print what depends on the passed modules")
  @ValueDescription("org:name")
    whatDependsOn: List[String] = Nil,
  @Group(OptionGroup.resolution)
  @HelpMessage("Print candidate artifact URLs")
  @Hidden
    candidateUrls: Boolean = false,

  @Group(OptionGroup.resolution)
  @HelpMessage("Print conflicts")
  @Hidden
    conflicts: Boolean = false,

  @Recurse
    sharedResolveOptions: SharedResolveOptions = SharedResolveOptions(),
  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Group(OptionGroup.resolution)
  @HelpMessage("Force printing / generating results, even if errored")
  @ExtraName("F")
    forcePrint: Boolean = false,
  @Group(OptionGroup.resolution)
  @Hidden
    retry: Option[String] = None,
  @Group(OptionGroup.resolution)
  @Hidden
    attempts: Option[Int] = None

) {
  // format: on
  def addApp(app: RawAppDescriptor): ResolveOptions =
    copy(sharedResolveOptions = sharedResolveOptions.addApp(app))
}

object ResolveOptions {
  implicit val parser                          = Parser[ResolveOptions]
  implicit lazy val help: Help[ResolveOptions] = Help.derive
}
