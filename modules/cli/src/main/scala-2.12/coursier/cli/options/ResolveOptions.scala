package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class ResolveOptions(

  @Help("Print the duration of each iteration of the resolution")
  @Short("B")
  @Value("Number of warm-up resolutions - if negative, doesn't print per iteration benchmark (less overhead)")
    benchmark: Int = 0,

  benchmarkCache: Boolean = false,

  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Help("Print dependencies as an inversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,
  @Help("Print what depends on the passed modules")
  @Value("org:name")
    whatDependsOn: List[String] = Nil,

  @Help("Print conflicts")
    conflicts: Boolean = false,

  @Recurse
    cacheOptions: shared.CacheOptions = shared.CacheOptions(),

  @Recurse
    repositoryOptions: shared.RepositoryOptions = shared.RepositoryOptions(),

  @Recurse
    resolutionOptions: shared.ResolutionOptions = shared.ResolutionOptions(),

  @Recurse
    dependencyOptions: shared.DependencyOptions = shared.DependencyOptions(),

  @Recurse
    outputOptions: shared.OutputOptions = shared.OutputOptions()

)
