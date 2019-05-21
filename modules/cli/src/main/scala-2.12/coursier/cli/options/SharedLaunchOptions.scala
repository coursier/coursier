package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.app.RawAppDescriptor
import coursier.cli.resolve.ResolveOptions

final case class SharedLaunchOptions(

  @Short("M")
  @Short("main")
    mainClass: String = "",

  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,

  @Help("Set Java properties before launching the app")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,

  @Recurse
    sharedLoaderOptions: SharedLoaderOptions = SharedLoaderOptions(),

  @Recurse
    resolveOptions: ResolveOptions = ResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions()
) {
  def addApp(app: RawAppDescriptor): SharedLaunchOptions =
    copy(
      // TODO Take app.properties into account
      sharedLoaderOptions = sharedLoaderOptions.addApp(app),
      resolveOptions = resolveOptions.addApp(app),
      artifactOptions = artifactOptions.addApp(app),
      mainClass = {
        if (mainClass.isEmpty)
          app.mainClass.fold("")(_.stripSuffix("?")) // FIXME '?' suffix means optional main class
        else
          mainClass
      },
      property = app.properties.props.map { case (k, v) => s"$k=$v" }.toList ++ property
    )
}

object SharedLaunchOptions {
  implicit val parser = Parser[SharedLaunchOptions]
  implicit val help = caseapp.core.help.Help[SharedLaunchOptions]
}
