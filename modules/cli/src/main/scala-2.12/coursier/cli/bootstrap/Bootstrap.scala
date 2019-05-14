package coursier.cli.bootstrap

import java.io.{File, PrintStream}
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.bootstrap.{Assembly, ClassLoaderContent, ClasspathEntry, LauncherBat}
import coursier.cli.fetch.Fetch
import coursier.cli.launch.{Launch, LaunchException}
import coursier.cli.options.BootstrapOptions
import coursier.cli.params.BootstrapParams
import coursier.cli.resolve.ResolveException
import coursier.cli.util.Native
import coursier.core.{Artifact, Resolution}
import coursier.util.{Sync, Task}

import scala.concurrent.ExecutionContext
import scala.scalanative.{build => sn}

object Bootstrap extends CaseApp[BootstrapOptions] {

  def createNativeBootstrap(
    params: BootstrapParams,
    files: Seq[File],
    mainClass: String
  ): Unit = {

    val log: String => Unit =
      if (params.sharedLaunch.resolve.output.verbosity >= 0)
        s => Console.err.println(s)
      else
        _ => ()

    val tmpDir = params.nativeBootstrap.workDir.toFile

    val config = sn.Config.empty
      .withGC(params.nativeBootstrap.gc)
      .withMode(params.nativeBootstrap.mode)
      .withLinkStubs(params.nativeBootstrap.linkStubs)
      .withClang(params.nativeBootstrap.clang)
      .withClangPP(params.nativeBootstrap.clangpp)
      .withLinkingOptions(params.nativeBootstrap.finalLinkingOptions)
      .withCompileOptions(params.nativeBootstrap.finalCompileOptions)
      .withTargetTriple(params.nativeBootstrap.targetTripleOpt.getOrElse {
        sn.Discover.targetTriple(params.nativeBootstrap.clang, tmpDir.toPath)
      })
      .withNativelib(params.nativeBootstrap.nativeLibOpt.getOrElse(
        sn.Discover.nativelib(files.map(_.toPath)).get
      ))

    try {
      Native.create(
        mainClass,
        files,
        params.specific.output.toFile,
        tmpDir,
        log,
        verbosity = params.sharedLaunch.resolve.output.verbosity,
        config = config
      )
    } finally {
      if (!params.nativeBootstrap.keepWorkDir)
        Native.deleteRecursive(tmpDir)
    }
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
        lazy val loader0 = Launch.loader(
          res,
          files,
          params.sharedLaunch.sharedLoader,
          params.sharedLaunch.artifact,
          params.sharedLaunch.extraJars.map(_.toUri.toURL)
        )

        params.sharedLaunch.mainClassOpt match {
          case Some(c) =>
            Task.point(c)
          case None =>
            Task.delay(Launch.mainClasses(loader0)).flatMap { m =>
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

    val params = BootstrapParams(options).toEither match {
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
      args.all,
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

    // kind of lame, I know
    var wroteBat = false

    if (params.specific.native)
      createNativeBootstrap(params, files.map(_._2), mainClass)
    else {
      val (asFiles, asUrls) = files.partition {
        case (a, _) =>
          params.specific.assembly || params.specific.standalone || (params.specific.embedFiles && a.url.startsWith("file:"))
      }

      val files0 = asFiles.map(_._2)
      val urls = asUrls.map(_._1.url)

      if (params.specific.createBatFile && !params.specific.force && Files.exists(params.specific.batOutput)) {
        System.err.println(s"Error: ${params.specific.batOutput} already exists, use -f option to force erasing it.")
        sys.exit(1)
      }

      if (params.specific.assembly)
        Assembly.create(
          files0,
          params.specific.javaOptions,
          mainClass,
          output0,
          rules = params.specific.assemblyRules,
          withPreamble = params.specific.withPreamble,
          disableJarChecking = params.specific.disableJarCheckingOpt.getOrElse(false)
        )
      else {

        val artifacts = files.toMap

        val (done, isolatedArtifactFiles) =
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
                if (params.specific.standalone)
                  (Nil, m0.map(_._2))
                else
                  (m0.map(_._1), Nil)

              val updatedAcc = acc + (name -> ((subUrls, subFiles)))

              (done0, updatedAcc)
          }

        val parents = params.sharedLaunch.sharedLoader.loaderNames.toSeq.map { t =>
          val e = isolatedArtifactFiles.get(t)
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
          val doneFiles = isolatedArtifactFiles.toSeq.flatMap(_._2._2).toSet
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
          params.specific.javaOptions,
          deterministic = params.specific.deterministicOutput,
          withPreamble = params.specific.withPreamble,
          proguarded = params.specific.proguarded,
          disableJarChecking = params.specific.disableJarCheckingOpt.getOrElse {
            content.exists(_.entries.exists {
              case _: ClasspathEntry.Resource => true
              case _ => false
            })
          }
        )
      }

      if (params.specific.createBatFile) {
        LauncherBat.create(
          params.specific.batOutput,
          params.specific.javaOptions
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
