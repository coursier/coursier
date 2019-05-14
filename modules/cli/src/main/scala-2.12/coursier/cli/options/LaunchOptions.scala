package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.shared.{ArtifactOptions, SharedLoaderOptions}

final case class LaunchOptions(

  @Short("M")
  @Short("main")
    mainClass: String = "",

  @Short("J")
  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,

  @Recurse
    sharedLoaderOptions: SharedLoaderOptions = SharedLoaderOptions(),

  @Recurse
    resolveOptions: ResolveOptions = ResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions()
)

object LaunchOptions {
  implicit val parser = Parser[LaunchOptions]
  implicit val help = caseapp.core.help.Help[LaunchOptions]
}
