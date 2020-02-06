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

  @Help("Keep dependencies or artifacts in classpath order (that is, dependencies before dependees)")
    classpathOrder: Option[Boolean] = None,

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    resolutionOptions: ResolutionOptions = ResolutionOptions(),

  @Recurse
    dependencyOptions: DependencyOptions = DependencyOptions(),

  @Recurse
    outputOptions: OutputOptions = OutputOptions()

) {
  def addApp(app: RawAppDescriptor): ResolveOptions =
    copy(
      // TODO Take app.scalaVersion into account
      repositoryOptions = repositoryOptions.copy(
        repository = {
          val previous = repositoryOptions.repository
          previous ++ app.repositories.filterNot(previous.toSet)
        }
      ),
      dependencyOptions = dependencyOptions.copy(
        exclude = {
          val previous = dependencyOptions.exclude
          previous ++ app.exclusions.filterNot(previous.toSet)
        },
        native = app.launcherType == "scala-native"
      ),
      resolutionOptions = resolutionOptions.copy(
        scalaVersion = resolutionOptions.scalaVersion.orElse(
          app.scalaVersion
        )
      )
    )
}

object ResolveOptions {
  implicit val parser = Parser[ResolveOptions]
  implicit val help = caseapp.core.help.Help[ResolveOptions]
}
