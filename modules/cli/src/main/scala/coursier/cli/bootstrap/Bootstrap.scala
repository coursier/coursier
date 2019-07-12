package coursier.cli.bootstrap

import java.io.{File, PrintStream}
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.bootstrap.{Assembly, ClassLoaderContent, ClasspathEntry, LauncherBat}
import coursier.cli.fetch.Fetch
import coursier.cli.launch.{Launch, LaunchException}
import coursier.cli.native.NativeBuilder
import coursier.cli.resolve.{Resolve, ResolveException}
import coursier.core.{Artifact, Resolution}
import coursier.util.{Sync, Task}

import scala.concurrent.ExecutionContext

object Bootstrap extends CaseApp[BootstrapOptions] {

  def createNativeBootstrap(
    params: BootstrapParams,
    files: Seq[File],
    mainClass: String,
    pool: ExecutorService,
    nativeVersion: String
  ): Unit = {

    val log: String => Unit =
      if (params.sharedLaunch.resolve.output.verbosity >= 0)
        s => Console.err.println(s)
      else
        _ => ()

    val logger = params.sharedLaunch.resolve.output.logger()
    val cache = params.sharedLaunch.resolve.cache.cache(pool, logger)
    val repositories = params.sharedLaunch.resolve.repositories.repositories
    val fetch = coursier.Fetch(cache)
      .withRepositories(repositories)

    val nativeVersion0 = params.nativeBootstrap.nativeShortVersionOpt.getOrElse(
      nativeVersion
    )
    val builder = NativeBuilder.load(fetch, nativeVersion0)

    builder.build(
      mainClass,
      files,
      params.specific.output.toFile,
      params.nativeBootstrap,
      log,
      params.sharedLaunch.resolve.output.verbosity
    )
  }

  def task(
    params: BootstrapParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    userArgs: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(Resolution, Seq[(Artifact, File)], String)] =
    for {
      t <- Fetch.task(params.sharedLaunch.fetch, pool, dependencyArgs, stdout, stderr)
      (res, files) = t
      mainClass <- {
        params.sharedLaunch.mainClassOpt match {
          case Some(c) =>
            Task.point(c)
          case None =>
            Task.delay(Launch.mainClasses(files.map(_._2) ++ params.sharedLaunch.extraJars.map(_.toFile))).flatMap { m =>
              if (params.sharedLaunch.resolve.output.verbosity >= 2)
                System.err.println(
                  "Found main classes:\n" +
                    m.map { case ((vendor, title), mainClass) => s"  $mainClass (vendor: $vendor, title: $title)\n" }.mkString +
                    "\n"
                )
              Launch.retainedMainClassOpt(m, res.rootDependencies.headOption) match {
                case Some(c) =>
                  Task.point(c)
                case None =>
                  Task.fail(new LaunchException.NoMainClassFound)
              }
            }
        }
      }
    } yield (res, files, mainClass)


  def run(options: BootstrapOptions, args: RemainingArgs): Unit = {

    // get options and dependencies from apps if any
    val (options0, deps) = BootstrapParams(options).toEither.toOption.fold((options, args.all)) { initialParams =>
      val initialRepositories = initialParams.sharedLaunch.resolve.repositories.repositories
      val channels = initialParams.sharedLaunch.resolve.repositories.channels
      val pool = Sync.fixedThreadPool(initialParams.sharedLaunch.resolve.cache.parallel)
      val cache = initialParams.sharedLaunch.resolve.cache.cache(pool, initialParams.sharedLaunch.resolve.output.logger())
      val res = Resolve.handleApps(options, args.all, channels, initialRepositories, cache)(_.addApp(_))
      pool.shutdown()
      res
    }

    val params = BootstrapParams(options0).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) =>
        params0
    }

    val pool = Sync.fixedThreadPool(params.sharedLaunch.resolve.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)

    val output0 = params.specific.output
    if (!params.specific.force && Files.exists(output0)) {
      System.err.println(s"Error: $output0 already exists, use -f option to force erasing it.")
      sys.exit(1)
    }

    val t = task(
      params,
      pool,
      deps,
      Nil
    )

    val (res, files, mainClass) = t.attempt.unsafeRun()(ec) match {
      case Left(e: ResolveException) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.message)
        sys.exit(1)
      case Left(e: coursier.error.FetchError) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case Left(e: LaunchException.NoMainClassFound) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println("Cannot find default main class. Specify one with -M or --main-class.")
        sys.exit(1)
      case Left(e: LaunchException) if params.sharedLaunch.resolve.output.verbosity <= 1 =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case Left(e) => throw e
      case Right(t0) =>
        t0
    }

    val nativeVersion = res
      .rootDependencies
      .map(_.module.name.value)
      .flatMap { name =>
        val elems = name.split('_')
        if (elems.length >= 3)
          Seq(elems(elems.length - 2))
        else
          Nil
      }
      .filter(_.startsWith("native"))
      .map(_.stripPrefix("native"))
      .distinct
      .map(coursier.core.Version(_))
      .sorted
      .lastOption
      .map(_.repr)
      .getOrElse {
        // FIXME Throw here?
        "0.3"
      }

    // kind of lame, I know
    var wroteBat = false

    if (params.sharedLaunch.resolve.output.verbosity >= 1)
      System.err.println(s"Using scala-native version $nativeVersion")

    if (params.sharedLaunch.resolve.dependency.native)
      createNativeBootstrap(params, files.map(_._2), mainClass, pool, nativeVersion)
    else {
      val (asFiles, asUrls) = files.partition {
        case (a, _) =>
          params.specific.assembly || params.specific.standalone || params.specific.hybrid || (params.specific.embedFiles && a.url.startsWith("file:"))
      }

      val files0 = asFiles.map(_._2)
      val urls = asUrls.map(_._1.url)

      if (params.specific.createBatFile && !params.specific.force && Files.exists(params.specific.batOutput)) {
        System.err.println(s"Error: ${params.specific.batOutput} already exists, use -f option to force erasing it.")
        sys.exit(1)
      }

      val javaOptions =
        if (params.specific.assembly)
          params.specific.javaOptions ++ params.sharedLaunch.properties.map { case (k, v) => s"-D$k=$v" }
        else
          params.specific.javaOptions

      if (params.specific.assembly)
        Assembly.create(
          files0,
          javaOptions,
          mainClass,
          output0,
          rules = params.specific.assemblyRules,
          withPreamble = params.specific.withPreamble,
          disableJarChecking = params.specific.disableJarCheckingOpt.getOrElse(false)
        )
      else {

        val artifacts = files.toMap

        val (done, sharedFiles) =
          params.sharedLaunch.sharedLoader.loaderNames.foldLeft((Set.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
            case ((done, acc), name) =>

              val deps = params.sharedLaunch.sharedLoader.loaderDependencies.getOrElse(name, Nil)
              val subRes = res.subset(deps)

              val m = coursier.Artifacts.artifacts0(
                subRes,
                params.sharedLaunch.artifact.classifiers,
                Option(params.sharedLaunch.artifact.mainArtifacts).map(x => x),
                Option(params.sharedLaunch.artifact.artifactTypes)
              ).map(_._3)

              val m0 = m.filter(a => !done(a.url)).map(a => a.url -> artifacts.getOrElse(a, sys.error("should not happen")))
              val done0 = done ++ m0.map(_._1)

              val (subUrls, subFiles) =
                if (params.specific.standalone || params.specific.hybrid)
                  (Nil, m0.map(_._2))
                else
                  (m0.map(_._1), Nil)

              val updatedAcc = acc + (name -> ((subUrls, subFiles)))

              (done0, updatedAcc)
          }

        val parents = params.sharedLaunch.sharedLoader.loaderNames.map { t =>
          val e = sharedFiles.get(t)
          val urls = e.map(_._1).getOrElse(Nil).map { url =>
            ClasspathEntry.Url(url)
          }
          val files = e.map(_._2).getOrElse(Nil).map { f =>
            ClasspathEntry.Resource(
              f.getName,
              f.lastModified(),
              Files.readAllBytes(f.toPath)
            )
          }
          ClassLoaderContent(
            urls ++ files,
            t
          )
        }

        val main = {
          val doneFiles = sharedFiles.toSeq.flatMap(_._2._2).toSet
          val urls0 = urls.filterNot(done).map { url =>
            ClasspathEntry.Url(url)
          }
          val files = files0.filterNot(doneFiles).map { f =>
            ClasspathEntry.Resource(
              f.getName,
              f.lastModified(),
              Files.readAllBytes(f.toPath)
            )
          }
          ClassLoaderContent(urls0 ++ files)
        }

        val content = parents :+ main

        coursier.bootstrap.Bootstrap.create(
          content,
          mainClass,
          output0,
          javaOptions,
          javaProperties = params.sharedLaunch.properties,
          deterministic = params.specific.deterministicOutput,
          withPreamble = params.specific.withPreamble,
          proguarded = params.specific.proguarded,
          hybridAssembly = params.specific.hybrid,
          disableJarChecking = params.specific.disableJarCheckingOpt.getOrElse[Boolean] {
            coursier.bootstrap.Bootstrap.defaultDisableJarChecking(content)
          }
        )
      }

      if (params.specific.createBatFile) {
        LauncherBat.create(
          params.specific.batOutput,
          javaOptions
        )
        wroteBat = true
      }
    }

    if (params.sharedLaunch.resolve.output.verbosity >= 0) {
      System.err.println(s"Wrote $output0")
      if (wroteBat)
        System.err.println(s"Wrote ${params.specific.batOutput}")
    }
  }

}
