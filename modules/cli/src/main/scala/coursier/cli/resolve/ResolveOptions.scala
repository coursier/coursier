package coursier.cli.resolve

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.{
  CacheOptions,
  DependencyOptions,
  OptionGroup,
  OutputOptions,
  RepositoryOptions,
  ResolutionOptions
}
import coursier.install.RawAppDescriptor

// format: off
@ArgsName("org:name:version*|app-name[:version]")
@Help(
  "Resolve and print the transitive dependencies of one or more dependencies or an application.\n" +
  "Print the maven coordinates, does not download the artifacts.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs resolve org.http4s:http4s-dsl_2.12:0.18.21\n" +
  "$ cs resolve --tree org.http4s:http4s-dsl_2.12:0.18.21"
)
final case class ResolveOptions(

  @Group(OptionGroup.resolution)
  @Help("Print the duration of each iteration of the resolution (if negative, doesn't print per iteration benchmark -> less overhead)")
  @Hidden
  @Short("B")
  @Value("# warm-up resolutions")
    benchmark: Int = 0,

  @Group(OptionGroup.resolution)
  @Hidden
    benchmarkCache: Boolean = false,

  @Group(OptionGroup.resolution)
  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Group(OptionGroup.resolution)
  @Help("Print dependencies as a reversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,
  @Group(OptionGroup.resolution)
  @Help("Print what depends on the passed modules")
  @Value("org:name")
    whatDependsOn: List[String] = Nil,
  @Group(OptionGroup.resolution)
  @Help("Print candidate artifact URLs")
    candidateUrls: Boolean = false,

  @Group(OptionGroup.resolution)
  @Help("Print conflicts")
  @Hidden
    conflicts: Boolean = false,

  @Recurse
    sharedResolveOptions: SharedResolveOptions = SharedResolveOptions(),
  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Group(OptionGroup.resolution)
  @Help("Force printing / generating results, even if errored")
  @Short("F")
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
  implicit val parser = Parser[ResolveOptions]
  implicit val help   = caseapp.core.help.Help[ResolveOptions]
}
