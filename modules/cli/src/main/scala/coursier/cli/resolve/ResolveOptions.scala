package coursier.cli.resolve

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.install.SharedChannelOptions
import coursier.cli.options.{
  CacheOptions,
  DependencyOptions,
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

  @Help("Print the duration of each iteration of the resolution (if negative, doesn't print per iteration benchmark -> less overhead)")
  @Short("B")
  @Value("# warm-up resolutions")
    benchmark: Int = 0,

  benchmarkCache: Boolean = false,

  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Help("Print dependencies as a reversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,
  @Help("Print what depends on the passed modules")
  @Value("org:name")
    whatDependsOn: List[String] = Nil,
  @Help("Print candidate artifact URLs")
    candidateUrls: Boolean = false,

  @Help("Print conflicts")
    conflicts: Boolean = false,

  @Recurse
    sharedResolveOptions: SharedResolveOptions = SharedResolveOptions(),
  @Recurse
    channelOptions: SharedChannelOptions = SharedChannelOptions(),

  @Help("Force printing / generating results, even if errored")
  @Short("F")
    forcePrint: Boolean = false,

  retry: Option[String] = None,
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
