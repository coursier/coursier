package coursier.cli.bootstrap

import java.io.{File, PrintStream}
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp.core.RemainingArgs
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.fetch.Fetch
import coursier.cli.launch.{Launch, LaunchException}
import coursier.cli.options.OptionGroup
import coursier.cli.resolve.{Resolve, ResolveException}
import coursier.cli.Util.ValidatedExitOnError
import coursier.core.{
  Classifier,
  Dependency,
  Module,
  ModuleName,
  Organization,
  Repository,
  Resolution,
  Type
}
import coursier.install.{Channels, MainClass}
import coursier.jvm.JvmCache
import coursier.launcher.{
  ClassLoaderContent,
  ClassPathEntry,
  Generator,
  Parameters,
  Preamble,
  ScalaNativeGenerator
}
import coursier.install.GraalvmParams
import coursier.launcher.native.NativeBuilder
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import coursier.util.{Artifact, Sync, Task}

import scala.concurrent.ExecutionContext
import caseapp.core.help.HelpFormat

object Bootstrap extends CoursierCommand[BootstrapOptions] {
  override def group: String = CommandGroup.launcher

  def task(
    params: BootstrapParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(Resolution, Option[String], Option[String], Seq[(Artifact, File)], String)] =
    for {
      t <-
        Fetch.task(params.sharedLaunch.fetch(params.channel), pool, dependencyArgs, stdout, stderr)
      (res, scalaVersionOpt, platformOpt, files) = t
      mainClass <- {
        params.sharedLaunch.mainClassOpt match {
          case Some(c) =>
            Task.point(c)
          case None =>
            Task.delay(
              MainClass.mainClasses(files.map(_._2) ++ params.sharedLaunch.extraJars.map(_.toFile))
            ).flatMap { data =>
              if (params.sharedLaunch.resolve.output.verbosity >= 2)
                System.err.println(
                  "Found main classes:" + System.lineSeparator() +
                    data
                      .map { case ((vendor, title), mainClass) =>
                        s"  $mainClass (vendor: $vendor, title: $title)" + System.lineSeparator()
                      }
                      .mkString +
                    System.lineSeparator()
                )
              MainClass.retainedMainClassOpt(
                data,
                res
                  .rootDependencies
                  .headOption
                  .map(d => (d.module.organization.value, d.module.name.value))
              ) match {
                case Some(c) =>
                  Task.point(c)
                case None =>
                  Task.fail(new LaunchException.NoMainClassFound)
              }
            }
        }
      }
    } yield (res, scalaVersionOpt, platformOpt, files, mainClass)

  private def parentLoadersArtifacts(
    loaderDependencies: Seq[(String, Seq[JavaOrScalaDependency])],
    res: Resolution,
    classifiers: Set[Classifier],
    mainArtifacts: Option[Boolean],
    artifactTypes: Set[Type],
    scalaVersionOpt: Option[String],
    platformOpt: Option[String],
    classpathOrder: Boolean
  ): Seq[(String, Seq[Artifact])] = {

    val perLoaderResolutions = loaderDependencies
      .map {
        case (_, deps) =>
          res.subset(deps.map { dep =>
            dep.dependency(
              JavaOrScalaModule.scalaBinaryVersion(scalaVersionOpt.getOrElse("")),
              scalaVersionOpt.getOrElse(""),
              platformOpt.getOrElse("")
            )
          })
      }

    val perLoaderArtifacts = perLoaderResolutions
      .map { subRes =>
        coursier.Artifacts.artifacts(
          subRes,
          classifiers,
          mainArtifacts,
          Some(artifactTypes),
          classpathOrder
        ).map(_._3)
      }

    val perLoaderUniqueArtifacts = perLoaderArtifacts
      .scanLeft((Set.empty[String], Seq.empty[Artifact])) {
        case ((doneUrls, _), artifacts) =>
          val artifacts0 = artifacts.filter(a => !doneUrls(a.url))
          val doneUrls0  = doneUrls ++ artifacts0.map(_.url)
          (doneUrls0, artifacts0)
      }
      .map(_._2)
      .drop(1)

    val loaderNames = loaderDependencies.map(_._1)

    loaderNames.zip(perLoaderUniqueArtifacts)
  }

  private def classloaderContent(
    packaging: BootstrapSpecificParams.BootstrapPackaging,
    artifactFiles: Seq[(Artifact, File)]
  ): ClassLoaderContent = {
    val (asFiles, asUrls) =
      if (packaging.standalone || packaging.hybrid)
        (artifactFiles, Nil)
      else if (packaging.embedFiles)
        artifactFiles.partition {
          case (a, _) =>
            a.url.startsWith("file:")
        }
      else
        (Nil, artifactFiles)

    val urls0 = asUrls.map(_._1.url).map(ClassPathEntry.Url(_))
    val files0 = asFiles.map(_._2).map { f =>
      ClassPathEntry.Resource(
        f.getName,
        f.lastModified(),
        Files.readAllBytes(f.toPath)
      )
    }
    ClassLoaderContent(urls0 ++ files0)
  }

  private def defaultNativeVersion(deps: Seq[Dependency]): Option[String] =
    deps
      .map(_.module.name.value)
      .iterator
      .flatMap { name =>
        val elems = name.split('_')
        if (elems.length >= 3)
          Iterator.single(elems(elems.length - 2))
        else
          Iterator.empty
      }
      .filter(_.startsWith("native"))
      .map(_.stripPrefix("native"))
      .toSet
      .toVector
      .map(coursier.core.Version(_))
      .sorted
      .lastOption
      .map(_.repr)

  private def simpleFetchFunction(
    repositories: Seq[Repository],
    cache: Cache[Task]
  ): Seq[String] => Seq[File] = {

    val fetch = coursier.Fetch(cache)
      .withRepositories(repositories)

    deps =>
      val deps0 = deps.map { dep =>
        dep.split(":", 3) match {
          case Array(org, name, ver) =>
            Dependency(Module(Organization(org), ModuleName(name), Map.empty), ver)
          case _ => ???
        }
      }

      fetch
        .addDependencies(deps0: _*)
        .run()
  }

  def run(options: BootstrapOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) =
      BootstrapParams(options).toEither.toOption.fold((options, args.remaining)) { initialParams =>
        val initialRepositories = initialParams.sharedLaunch.resolve.repositories.repositories
        val channels            = initialParams.channel.channels
        pool = Sync.fixedThreadPool(initialParams.sharedLaunch.resolve.cache.parallel)
        val cache = initialParams.sharedLaunch.resolve.cache.cache(
          pool,
          initialParams.sharedLaunch.resolve.output.logger()
        )
        val channels0 = Channels(channels, initialRepositories, cache)
        val res       = Resolve.handleApps(options, args.remaining, channels0)(_.addApp(_))
        res
      }

    val params = BootstrapParams(options0).exitOnError()

    if (pool == null)
      pool = Sync.fixedThreadPool(params.sharedLaunch.resolve.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)

    val output0 = params.specific.output
    if (!params.specific.force && Files.exists(output0)) {
      System.err.println(s"Error: $output0 already exists, use -f option to force erasing it.")
      sys.exit(1)
    }

    val t = task(
      params,
      pool,
      deps
    )

    val (res, scalaVersionOpt, platformOpt, files, mainClass) = t.attempt.unsafeRun()(ec) match {
      case Left(e: ResolveException) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.message)
        sys.exit(1)
      case Left(e: coursier.error.FetchError)
          if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case Left(e: LaunchException.NoMainClassFound)
          if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println("Cannot find default main class. Specify one with -M or --main-class.")
        sys.exit(1)
      case Left(e: LaunchException) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case Left(e) => throw e
      case Right(t0) =>
        t0
    }

    var wroteBat = false

    val javaOptions =
      if (params.specific.assembly || params.specific.manifestJar)
        params.sharedLaunch.javaOptions ++ params.sharedLaunch.properties.map { case (k, v) =>
          s"-D$k=$v"
        }
      else
        params.sharedLaunch.javaOptions

    val params0 =
      if (params.sharedLaunch.resolve.dependency.native) {

        val nativeVersion = params.nativeShortVersionOpt
          .orElse(defaultNativeVersion(res.rootDependencies))
          .getOrElse {
            // FIXME Throw here?
            "0.3"
          }

        if (params.sharedLaunch.resolve.output.verbosity >= 1)
          System.err.println(s"Using scala-native version $nativeVersion")

        val log: String => Unit =
          if (params.sharedLaunch.resolve.output.verbosity >= 0)
            s => Console.err.println(s)
          else
            _ => ()

        val fetch0 = {
          val logger = params.sharedLaunch.resolve.output.logger()
          simpleFetchFunction(
            params.sharedLaunch.resolve.repositories.repositories,
            params.sharedLaunch.resolve.cache.cache(pool, logger)
          )
        }

        Parameters.ScalaNative(fetch0, mainClass, nativeVersion)
          .withJars(files.map(_._2))
          .withOptions(params.nativeOptions)
          .withLog(log)
          .withVerbosity(params.sharedLaunch.resolve.output.verbosity)
          .withPython(params.sharedLaunch.python)
      }
      else if (params.specific.nativeImage) {
        val fetch0 = {
          val logger = params.sharedLaunch.resolve.output.logger()
          simpleFetchFunction(
            params.sharedLaunch.resolve.repositories.repositories,
            params.sharedLaunch.resolve.cache.cache(pool, logger)
          )
        }

        val graalvmVersion = GraalvmParams.resolveGraalVmOptions(params.specific.graalvmVersionOpt)

        val handle = params
          .specific
          .jvmCache(
            params.sharedLaunch.resolve.cache.cache(
              pool,
              params.sharedLaunch.resolve.output.logger()
            )
          )
        val javaHomeTask = handle.get(graalvmVersion)
        val javaHome     = javaHomeTask.unsafeRun()(ExecutionContext.fromExecutorService(pool))

        Parameters.NativeImage(mainClass, fetch0)
          .withJars(files.map(_._2))
          .withGraalvmVersion(params.specific.graalvmVersionOpt)
          .withGraalvmJvmOptions(params.specific.graalvmJvmOptions)
          .withGraalvmOptions(params.specific.graalvmOptions ++ args.unparsed)
          .withIntermediateAssembly(params.specific.nativeImageIntermediateAssembly)
          .withJavaHome(javaHome)
          .withVerbosity(params.sharedLaunch.resolve.output.verbosity)
      }
      else {

        val foundNonErasableFile = params.specific.createBatFile &&
          !params.specific.force &&
          Files.exists(params.specific.batOutput)
        if (foundNonErasableFile) {
          System.err.println(
            s"Error: ${params.specific.batOutput} already exists, use -f option to force erasing it."
          )
          sys.exit(1)
        }

        val preambleOpt =
          if (params.specific.withPreamble)
            Some(
              coursier.launcher.Preamble()
                .withJavaOpts(javaOptions)
                .withJvmOptionFile(params.jvmOptionFile)
            )
          else
            None

        if (params.specific.assembly)
          Parameters.Assembly()
            .withFiles(files.map(_._2))
            .withMainClass(mainClass)
            .withRules(params.specific.assemblyRules)
            .withBaseManifest(params.specific.baseManifestOpt)
            .withPreambleOpt(preambleOpt)
        else if (params.specific.manifestJar)
          Parameters.ManifestJar(files.map(_._2), mainClass)
            .withPreambleOpt(preambleOpt)
        else {

          val artifactFiles = files.toMap

          val parents = parentLoadersArtifacts(
            params.sharedLaunch.sharedLoader.loaderNames.map { name =>
              val deps = params.sharedLaunch.sharedLoader.loaderDependencies.getOrElse(name, Nil)
              name -> deps
            },
            res,
            params.sharedLaunch.artifact.classifiers,
            Option(params.sharedLaunch.artifact.mainArtifacts).map(x => x),
            params.sharedLaunch.artifact.artifactTypes,
            scalaVersionOpt,
            platformOpt,
            params.sharedLaunch.resolve.classpathOrder.getOrElse(true)
          )

          val main = {
            val inParents = parents.flatMap(_._2).map(_.url).toSet
            files.map(_._1).filter(a => !inParents(a.url))
          }

          val content = (parents :+ ("" -> main)).map {
            case (name, artifacts) =>
              val artifactFiles0 = artifacts
                .map(a => (a, artifactFiles.getOrElse(a, sys.error("should not happen"))))
              classloaderContent(params.specific.bootstrapPackaging, artifactFiles0)
                .withLoaderName(name)
          }

          val params0 = Parameters.Bootstrap(content, mainClass)
            .withJavaProperties(params.sharedLaunch.properties)
            .withDeterministic(params.specific.deterministicOutput)
            .withPreambleOpt(preambleOpt)
            .withProguarded(params.specific.proguarded)
            .withHybridAssembly(params.specific.hybrid)
            .withDisableJarChecking(params.specific.disableJarCheckingOpt)
            .withPython(params.sharedLaunch.python)
            .withPythonJep(params.sharedLaunch.pythonJep)

          if (params.sharedLaunch.python) {
            val task = Fetch.task(
              params.sharedLaunch.fetch(params.channel),
              pool,
              Seq("io.github.alexarchambault.python:interface:0.1.0")
            )
            val (_, _, _, pythonFiles) = task.attempt.unsafeRun()(ec) match {
              case Left(e: ResolveException) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
                System.err.println(e.message)
                sys.exit(1)
              case Left(e: coursier.error.FetchError)
                  if params.sharedLaunch.resolve.output.verbosity <= 1 =>
                System.err.println(e.getMessage)
                sys.exit(1)
              case Left(e)   => throw e
              case Right(t0) => t0
            }

            val pythonContent = Seq(
              classloaderContent(params.specific.bootstrapPackaging, pythonFiles)
            )

            params0.addExtraContent("python", pythonContent)
          }
          else
            params0
        }
      }

    Generator.generate(params0, output0)

    if (params.specific.createBatFile) {
      val content = Preamble()
        .withKind(Preamble.Kind.Bat)
        .withJarPath("%~dp0\\%~n0")
        .withJavaOpts(javaOptions)
        .value
      Files.write(params.specific.batOutput, content)
      wroteBat = true
    }

    if (params.sharedLaunch.resolve.output.verbosity >= 0) {
      System.err.println(s"Wrote $output0")
      if (wroteBat)
        System.err.println(s"Wrote ${params.specific.batOutput}")
    }
  }

}
