package coursier.cli.resolve

import caseapp._
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
@ArgsName("org:name:version|app-name[:version]*")
final case class SharedResolveOptions(
  @Group(OptionGroup.resolution) // with ResolutionOptions
  @Hidden
  @HelpMessage("Keep dependencies or artifacts in classpath order (that is, dependencies before dependees)")
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
  // format: on
  def addApp(app: RawAppDescriptor): SharedResolveOptions =
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
      ),
      cacheOptions = cacheOptions.copy(
        ttl = cacheOptions.ttl.orElse(
          // if an app is passed, and we have everything to start it locally,
          // start the app straightaway, no need to check for updates
          Some("Inf")
        )
      )
    )
}

object SharedResolveOptions {
  lazy val parser: Parser[SharedResolveOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[SharedResolveOptions, parser.D] = parser
  implicit lazy val help: Help[SharedResolveOptions]                      = Help.derive
}
