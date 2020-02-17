package coursier.cli.deprecated

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, DependencyOptions, OutputOptions, RepositoryOptions, ResolutionOptions}

final case class CommonOptions(

  @Help("Print the duration of each iteration of the resolution")
  @Short("B")
  @Value("Number of warm-up resolutions - if negative, doesn't print per iteration benchmark (less overhead)")
    benchmark: Int = 0,

  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Help("Print dependencies as a reversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,

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
  def verbosityLevel: Int =
    Tag.unwrap(outputOptions.verbose) - Tag.unwrap(outputOptions.quiet)
}

object CommonOptions {
  implicit val parser = Parser[CommonOptions]
  implicit val help = caseapp.core.help.Help[CommonOptions]
}
