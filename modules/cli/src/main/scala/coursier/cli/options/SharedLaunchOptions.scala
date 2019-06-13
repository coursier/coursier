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

  def app: RawAppDescriptor =
    RawAppDescriptor(
      Nil,
      shared = sharedLoaderOptions.shared,
      repositories = {
        val default =
          if (resolveOptions.repositoryOptions.noDefault) List()
          else List("central") // ?
        default ::: resolveOptions.repositoryOptions.repository
      },
      exclusions = resolveOptions.dependencyOptions.exclude,
      launcherType = {
        if (resolveOptions.dependencyOptions.native) "scala-native"
        else "bootstrap"
      },
      classifiers = {
        val l = artifactOptions.classifier
        val default = if (artifactOptions.default0) List("_") else Nil
        val c = default ::: l
        if (c == List("_"))
          Nil
        else
          c
      },
      artifactTypes = artifactOptions.artifactType,
      mainClass = Some(mainClass).filter(_.nonEmpty),
      properties = RawAppDescriptor.Properties {
        property.map { s =>
          s.split("=", 2) match {
            case Array(k, v) =>
              (k, v)
            case Array(k) =>
              (k, "")
          }
        }
      }
    )
}

object SharedLaunchOptions {
  implicit val parser = Parser[SharedLaunchOptions]
  implicit val help = caseapp.core.help.Help[SharedLaunchOptions]
}
