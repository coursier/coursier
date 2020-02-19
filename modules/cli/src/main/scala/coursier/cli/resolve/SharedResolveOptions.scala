package coursier.cli.resolve

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.{CacheOptions, DependencyOptions, OutputOptions, RepositoryOptions, ResolutionOptions}
import coursier.install.RawAppDescriptor

@ArgsName("org:name:version|app-name[:version]*")
final case class SharedResolveOptions(

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
      )
    )
}

object SharedResolveOptions {
  implicit val parser = Parser[SharedResolveOptions]
  implicit val help = caseapp.core.help.Help[SharedResolveOptions]
}
