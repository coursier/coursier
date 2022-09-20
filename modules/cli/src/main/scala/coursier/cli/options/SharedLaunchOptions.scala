package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.install.SharedChannelOptions
import coursier.cli.resolve.SharedResolveOptions
import coursier.install.RawAppDescriptor

// format: off
final case class SharedLaunchOptions(

  @Group(OptionGroup.launch)
  @Short("M")
  @Short("main")
    mainClass: String = "",

  @Group(OptionGroup.launch)
  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Help("Set Java properties before launching the app")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Help("Add Java command-line options")
  @Value("option")
    javaOpt: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Hidden
    pythonJep: Option[Boolean] = None,
  @Group(OptionGroup.launch)
  @Hidden
    python: Option[Boolean] = None,

  @Recurse
    sharedLoaderOptions: SharedLoaderOptions = SharedLoaderOptions(),

  @Recurse
    resolveOptions: SharedResolveOptions = SharedResolveOptions(),

  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions()
) {
  // format: on

  def addApp(app: RawAppDescriptor): SharedLaunchOptions =
    copy(
      sharedLoaderOptions = sharedLoaderOptions.addApp(app),
      resolveOptions = resolveOptions.addApp(app),
      artifactOptions = artifactOptions.addApp(app),
      mainClass =
        if (mainClass.isEmpty)
          app.mainClass.fold("")(_.stripSuffix("?")) // FIXME '?' suffix means optional main class
        else
          mainClass,
      javaOpt = app.javaOptions ++ javaOpt,
      property = app.properties.props.map { case (k, v) => s"$k=$v" }.toList ++ property,
      pythonJep = pythonJep.orElse(if (app.jna.contains("python-jep")) Some(true) else None),
      python = python.orElse(if (app.jna.contains("python")) Some(true) else None)
    )

  def app: RawAppDescriptor =
    RawAppDescriptor(Nil)
      .withShared(sharedLoaderOptions.shared)
      .withRepositories {
        val default =
          if (resolveOptions.repositoryOptions.noDefault) List()
          else List("central") // ?
        default ::: resolveOptions.repositoryOptions.repository
      }
      .withExclusions(resolveOptions.dependencyOptions.exclude)
      .withLauncherType {
        if (resolveOptions.dependencyOptions.native) "scala-native"
        else "bootstrap"
      }
      .withClassifiers {
        val l       = artifactOptions.classifier
        val default = if (artifactOptions.default0) List("_") else Nil
        val c       = default ::: l
        if (c == List("_"))
          Nil
        else
          c
      }
      .withArtifactTypes(artifactOptions.artifactType)
      .withMainClass(Some(mainClass).filter(_.nonEmpty))
      .withProperties(
        RawAppDescriptor.Properties {
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
      .withJna {
        if (pythonJep.getOrElse(false)) List("python-jep")
        else if (python.getOrElse(false)) List("python")
        else Nil
      }
}

object SharedLaunchOptions {
  implicit val parser = Parser[SharedLaunchOptions]
  implicit val help   = caseapp.core.help.Help[SharedLaunchOptions]
}
