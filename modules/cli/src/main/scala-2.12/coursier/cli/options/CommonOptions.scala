package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class CommonOptions(

  @Help("Print the duration of each iteration of the resolution")
  @Short("B")
  @Value("Number of warm-up resolutions - if negative, doesn't print per iteration benchmark (less overhead)")
    benchmark: Int = 0,

  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Help("Print dependencies as an inversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,

  @Help("Specify path for json output")
  @Short("j")
    jsonOutputFile: String = "",

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

) {
  def verbosityLevel = outputOptions.verbosityLevel
}

object CommonOptions {
  implicit val parser = Parser[CommonOptions]
  implicit val help = caseapp.core.help.Help[CommonOptions]
}
