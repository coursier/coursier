package coursier.cli.resolve

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, DependencyOptions, OutputOptions, RepositoryOptions, ResolutionOptions}
import coursier.install.RawAppDescriptor

@ArgsName("org:name:version|app-name[:version]*")
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

  @Help("Force printing / generating results, even if errored")
  @Short("F")
    forcePrint: Boolean = false,

  retry: Option[String] = None,
  attempts: Option[Int] = None

) {
  def addApp(app: RawAppDescriptor): ResolveOptions =
    copy(sharedResolveOptions = sharedResolveOptions.addApp(app))
}

object ResolveOptions {
  implicit val parser = Parser[ResolveOptions]
  implicit val help = caseapp.core.help.Help[ResolveOptions]
}
