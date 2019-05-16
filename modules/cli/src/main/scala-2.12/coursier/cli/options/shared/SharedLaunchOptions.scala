package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.resolve.ResolveOptions

final case class SharedLaunchOptions(

  @Short("M")
  @Short("main")
    mainClass: String = "",

  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,

  @Recurse
    sharedLoaderOptions: SharedLoaderOptions = SharedLoaderOptions(),

  @Recurse
    resolveOptions: ResolveOptions = ResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions()
)

object SharedLaunchOptions {
  implicit val parser = Parser[SharedLaunchOptions]
  implicit val help = caseapp.core.help.Help[SharedLaunchOptions]
}
