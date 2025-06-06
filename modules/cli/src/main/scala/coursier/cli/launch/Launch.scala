package coursier.cli.launch

import java.io.{File, PrintStream}
import java.lang.reflect.Modifier
import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Path}
import java.util.concurrent.ExecutorService

import ai.kien.python.Python
import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cache.{ArchiveCache, FileCache}
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.cli.fetch.Fetch
import coursier.cli.params.{ArtifactParams, SharedLaunchParams, SharedLoaderParams}
import coursier.cli.resolve.{Resolve, ResolveException}
import coursier.cli.Util.ValidatedExitOnError
import coursier.core.{Dependency, Resolution}
import coursier.env.EnvironmentUpdate
import coursier.error.ResolutionError
import coursier.exec.Execve
import coursier.install.{Channels, MainClass, RawAppDescriptor}
import coursier.launcher.{
  BootstrapGenerator,
  ClassLoaderContent,
  ClassPathEntry,
  MergeRule,
  Parameters
}
import coursier.parse.{DependencyParser, JavaOrScalaDependency, JavaOrScalaModule}
import coursier.paths.Jep
import coursier.util.{Artifact, Sync, Task}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Properties, Success}

object Launch extends CoursierCommand[LaunchOptions] {

  def baseLoader: ClassLoader = {

    @tailrec
    def rootLoader(cl: ClassLoader): ClassLoader =
      Option(cl.getParent) match {
        case Some(par) => rootLoader(par)
        case None      => cl
      }

    rootLoader(ClassLoader.getSystemClassLoader)
  }

  def launchFork(
    hierarchy: Seq[(Option[String], Array[(Option[Artifact], File)])],
    mainClass: String,
    args: Seq[String],
    javaPath: String,
    javaOptions: Seq[String],
    properties: Seq[(String, String)],
    extraEnv: EnvironmentUpdate,
    verbosity: Int,
    execve: Option[Boolean],
    hybrid: Boolean,
    useBootstrap: Boolean,
    assemblyRules: Seq[MergeRule],
    workDirOpt: Option[Path]
  ): Either[LaunchException, () => Int] = {

    val cp = hierarchy match {
      case Seq((None, files)) if !useBootstrap =>
        Right(files.map(_._2.getAbsolutePath).mkString(File.pathSeparator))
      case _ =>
        val content = hierarchy.map {
          case (nameOpt, files) =>
            val entries = files.toSeq.map { case (aOpt, f) =>
              if (hybrid)
                ClassPathEntry.Resource(
                  f.getName,
                  f.lastModified(),
                  Files.readAllBytes(f.toPath)
                )
              else {
                val url = aOpt
                  .filter(_.authentication.isEmpty) // that might be surprising down-the-line…
                  .map(_.url)
                  .getOrElse(f.toURI.toASCIIString)
                ClassPathEntry.Url(url)
              }
            }
            ClassLoaderContent(entries, nameOpt.getOrElse(""))
        }
        val tmpFile = workDirOpt match {
          case Some(workDir) =>
            Files.createDirectories(workDir)
            Files.createTempFile(workDir, "coursier-launch-", ".jar")
          case None =>
            val f = Files.createTempFile("coursier-launch-", ".jar")
            // not sure that will be fine if we use execve instead of ProcessBuilder
            // (does the shutdown hook run deleting the file? or doesn't it, leaving junk behind?)
            Runtime.getRuntime().addShutdownHook(
              new Thread {
                override def run(): Unit =
                  Files.deleteIfExists(f)
              }
            )
            f
        }
        val bootstrapParams = Parameters.Bootstrap(content, mainClass)
          .withPreambleOpt(None)
          .withHybridAssembly(hybrid)
          .withRules(assemblyRules)
        BootstrapGenerator.generate(bootstrapParams, tmpFile)
        Left(tmpFile.toAbsolutePath.toFile)
    }

    val execve0 = Execve.available() && new File(javaPath).exists() && execve.getOrElse {
      // See comment above - we keep the current process alive to clean up the
      // temporary bootstrap if cp is left
      cp.isRight
    }

    val cpOpts = cp match {
      case Right(cp0) => Seq("-cp", cp0, mainClass)
      case Left(jar)  => Seq("-jar", jar.getAbsolutePath)
    }

    val cmd = Seq(javaPath) ++
      javaOptions ++
      properties.map { case (k, v) => s"-D$k=$v" } ++
      cpOpts ++
      args

    lazy val b = {
      val b0 = new ProcessBuilder(cmd: _*)
      b0.inheritIO()

      val env = b0.environment()
      for ((k, v) <- extraEnv.transientUpdates())
        env.put(k, v)
      b0
    }

    Right {
      () =>
        if (verbosity >= 1)
          System.err.println(
            "Running " + cmd.map("\"" + _.replace("\"", "\\\"") + "\"").mkString(" ")
          )
        if (execve0) {
          val fullEnv = (sys.env ++ extraEnv.transientUpdates())
            .iterator
            .map {
              case (k, v) =>
                s"$k=$v"
            }
            .toArray
            .sorted
          Execve.execve(new File(javaPath).getAbsolutePath, cmd.toArray, fullEnv)
          // execve should not return
          sys.error("something went wrong")
        }
        else {
          val p = b.start()
          p.waitFor()
        }
    }
  }

  def launch(
    hierarchy: Seq[(Option[String], Array[File])],
    mainClass: String,
    args: Seq[String],
    properties: Seq[(String, String)]
  ): Either[LaunchException, () => Unit] = {

    val loader0 = loader(
      hierarchy.map {
        case (nameOpt, files) =>
          (nameOpt, files.map(_.toURI.toURL))
      }
    )

    for {
      cls <- {
        try Right(loader0.loadClass(mainClass))
        catch {
          case e: ClassNotFoundException =>
            Left(new LaunchException.MainClassNotFound(mainClass, e))
        }
      }
      method <- {
        try {
          val m = cls.getMethod("main", classOf[Array[String]])
          m.setAccessible(true)
          Right(m)
        }
        catch {
          case e: NoSuchMethodException =>
            Left(new LaunchException.MainMethodNotFound(cls, e))
        }
      }
      _ <- {
        val isStatic = Modifier.isStatic(method.getModifiers)
        if (isStatic)
          Right(())
        else
          Left(new LaunchException.NonStaticMainMethod(cls, method))
      }
    } yield {
      () =>
        val currentThread      = Thread.currentThread()
        val previousLoader     = currentThread.getContextClassLoader
        val previousProperties = properties.map(_._1).map(k => k -> Option(System.getProperty(k)))
        try {
          currentThread.setContextClassLoader(loader0)
          for ((k, v) <- properties)
            sys.props(k) = v
          method.invoke(null, args.toArray)
        }
        catch {
          case e: java.lang.reflect.InvocationTargetException =>
            throw Option(e.getCause).getOrElse(e)
        }
        finally {
          currentThread.setContextClassLoader(previousLoader)
          previousProperties.foreach {
            case (k, None)    => System.clearProperty(k)
            case (k, Some(v)) => System.setProperty(k, v)
          }
        }
    }
  }

  def loaderHierarchy(
    res: Resolution,
    files: Seq[(Artifact, File)],
    scalaVersionOpt: Option[String],
    platformOpt: Option[String],
    sharedLoaderParams: SharedLoaderParams,
    artifactParams: ArtifactParams,
    extraJars: Seq[File],
    classpathOrder: Boolean
  ): Seq[(Option[String], Array[(Option[Artifact], File)])] = {
    val fileMap      = files.toMap
    val alreadyAdded = Set.empty[File] // unused???
    val parents: Seq[(Some[String], Array[(Option[Artifact], File)])] =
      sharedLoaderParams
        .loaderNames
        .map { name =>
          val deps = sharedLoaderParams.loaderDependencies.getOrElse(name, Nil)
          val subRes = res.subset0(deps.map(_.dependency(
            JavaOrScalaModule.scalaBinaryVersion(scalaVersionOpt.getOrElse("")),
            scalaVersionOpt.getOrElse(""),
            platformOpt.getOrElse("")
          ))).toTry.get
          val artifacts = coursier.Artifacts.artifacts(
            subRes,
            artifactParams.classifiers,
            Option(artifactParams.mainArtifacts).map(x => x),
            Option(artifactParams.artifactTypes),
            classpathOrder
          ).map(_._3)
          val files0 = artifacts
            .map(a => (Option(a), fileMap.getOrElse(a, sys.error("should not happen"))))
            .filter { case (_, f) => !alreadyAdded(f) }
          Some(name) -> files0.toArray
        }
    val cp =
      files
        .filter { case (_, f) => !alreadyAdded(f) }
        .map { case (a, f) => (Option(a), f) }
        .toArray ++
        extraJars
          .map((None, _))
    parents :+ (None, cp)
  }

  def loader(hierarchy: Seq[(Option[String], Array[URL])]): ClassLoader =
    hierarchy.foldLeft(baseLoader) {
      case (parent, (None, urls)) =>
        new URLClassLoader(urls, parent)
      case (parent, (Some(name), urls)) =>
        new SharedClassLoader(urls, parent, Array(name))
    }

  def mainClass(
    params: SharedLaunchParams,
    files: Seq[File],
    mainDependencyOpt: Option[Dependency]
  ): Task[String] =
    params.mainClassOpt match {
      case Some(c) =>
        Task.point(c)
      case None =>
        Task.delay(MainClass.mainClassesWithMainOne(files)).flatMap {
          case (fromFirstJarOpt, map) =>
            if (params.resolve.output.verbosity >= 2) {
              System.err.println(s"Main class in first JAR: $fromFirstJarOpt")
              System.err.println(
                "Found main classes:" + System.lineSeparator() +
                  map
                    .map { case ((vendor, title), mainClass) =>
                      s"  $mainClass (vendor: $vendor, title: $title)" + System.lineSeparator()
                    }
                    .mkString +
                  System.lineSeparator()
              )
            }
            MainClass.retainedMainClassOpt(
              map,
              mainDependencyOpt.map(d => (d.module.organization.value, d.module.name.value))
            ).orElse(fromFirstJarOpt) match {
              case Some(c) =>
                Task.point(c)
              case None =>
                Task.fail(new LaunchException.NoMainClassFound)
            }
        }
    }

  def launchCall(
    params: LaunchParams,
    javaPath: String,
    mainClass0: String,
    files: Seq[File],
    hierarchy: Seq[(Option[String], Array[(Option[Artifact], File)])],
    props: Seq[(String, String)],
    extraEnv: EnvironmentUpdate,
    userArgs: Seq[String],
    cache: FileCache[Task]
  ) = {

    val (jlp, jepExtraJar) =
      if (params.jep) {
        val jepLocation = Jep.location()
        if (params.shared.resolve.output.verbosity >= 1)
          System.err.println(s"Found jep location: $jepLocation")
        val jepJar = Jep.jar(jepLocation)
        if (params.shared.resolve.output.verbosity >= 1)
          System.err.println(s"Found jep JAR: $jepJar")
        val props = Seq(
          "java.library.path" -> jepLocation.getAbsolutePath
        )

        (props, Some(jepJar))
      }
      else
        (Nil, None)

    val (pythonJepProps, pythonJepEnv) =
      if (params.shared.pythonJep || params.jep)
        try {
          val home = Jep.pythonHome()
          val props = Jep.pythonProperties()
            .iterator()
            .asScala
            .map(e => (e.getKey, e.getValue))
            .toVector
          (props, EnvironmentUpdate(Seq("PYTHONHOME" -> home), Nil))
        }
        catch {
          case NonFatal(e) =>
            if (params.shared.resolve.output.verbosity >= 1)
              System.err.println(s"Cannot get python home: $e")
            (Nil, EnvironmentUpdate.empty)
        }
      else
        (Nil, EnvironmentUpdate.empty)

    val pythonProps =
      if (params.shared.python)
        Python().scalapyProperties match {
          case Success(props) => props
          case Failure(e) =>
            if (params.shared.resolve.output.verbosity >= 2)
              throw new Exception(e)
            else if (params.shared.resolve.output.verbosity >= 1)
              System.err.println(s"Cannot get python properties: $e")
            Nil
        }
      else
        Nil

    val extraJars = params.shared.extraJars.map(_.toFile) ++ jepExtraJar.toSeq
    val hierarchy0 =
      if (extraJars.isEmpty) hierarchy
      else {
        val (nameOpt, files0) = hierarchy.last
        hierarchy.init :+ ((nameOpt, files0 ++ extraJars.map((None, _))))
      }

    val jcp =
      hierarchy0 match {
        case Seq((None, files)) =>
          Seq(
            "java.class.path" -> files.map(_._2.getAbsolutePath).mkString(File.pathSeparator)
          )
        case _ =>
          Nil
      }

    val properties0 = {
      val m = new java.util.LinkedHashMap[String, String]
      // order matters - jcp first, so that it can be referenced from subsequent variables before expansion
      def allProps =
        jcp.iterator ++ jlp.iterator ++ pythonJepProps.iterator ++ pythonProps.iterator ++ props.iterator
      for ((k, v) <- allProps)
        m.put(k, v)
      val m0 = coursier.paths.Util.expandProperties(System.getProperties, m)
      // don't unnecessarily inject java.class.path - passing -cp to the Java invocation is enough
      if (params.fork && props.forall(_._1 != "java.class.path"))
        m0.remove("java.class.path")
      val b = new ListBuffer[(String, String)]
      m0.forEach(
        new java.util.function.BiConsumer[String, String] {
          def accept(k: String, v: String) =
            b += k -> v
        }
      )
      b.result()
    }

    val asyncProfilerOptions = params.asyncProfilerVersion match {
      case Some(asyncProfilerVersion) =>
        val archiveCache = ArchiveCache().withCache(cache)
        val (url, pathInArchive) =
          if (Properties.isMac)
            (
              s"https://github.com/async-profiler/async-profiler/releases/download/v$asyncProfilerVersion/async-profiler-$asyncProfilerVersion-macos.zip",
              s"async-profiler-$asyncProfilerVersion-macos/lib/libasyncProfiler.dylib"
            )
          else if (Properties.isLinux)
            sys.props.get("os.arch") match {
              case Some("amd64" | "x86_64") =>
                (
                  s"https://github.com/async-profiler/async-profiler/releases/download/v$asyncProfilerVersion/async-profiler-$asyncProfilerVersion-linux-x64.tar.gz",
                  s"async-profiler-$asyncProfilerVersion-linux-x64/lib/libasyncProfiler.so"
                )
              case Some("arm64" | "aarch64") =>
                (
                  s"https://github.com/async-profiler/async-profiler/releases/download/v$asyncProfilerVersion/async-profiler-$asyncProfilerVersion-linux-arm64.tar.gz",
                  s"async-profiler-$asyncProfilerVersion-linux-arm64/lib/libasyncProfiler.so"
                )
              case Some(other) =>
                sys.error(s"Async-profiler not supported on current CPU ($other)")
              case None =>
                sys.error("Cannot get CPU for async-profiler")
            }
          else
            sys.error(s"Async-profiler not supported on current OS (${sys.props("os.name")})")

        val dir = archiveCache.get(Artifact(url)).unsafeRun(wrapExceptions = true)(cache.ec) match {
          case Left(err)  => throw new Exception(err)
          case Right(res) => res
        }
        val agentFile = new File(dir, pathInArchive)
        Seq(s"-agentpath:$agentFile=${params.asyncProfilerOptions.mkString(",")}")
      case None =>
        Nil
    }

    if (params.fork || params.hybrid || params.useBootstrap)
      launchFork(
        hierarchy0,
        mainClass0,
        userArgs,
        javaPath,
        asyncProfilerOptions ++ params.shared.javaOptions,
        properties0,
        pythonJepEnv + extraEnv,
        params.shared.resolve.output.verbosity,
        params.execve,
        params.hybrid,
        params.useBootstrap,
        params.assemblyRules,
        params.workDir
      )
        .map(f => () => Some(f()))
    else
      launch(
        hierarchy0.map { case (name, f) => (name, f.map(_._2)) },
        mainClass0,
        userArgs,
        properties0
      )
        // format: off
        .map {f => () => f(); None}
        // format: on
  }

  // same as task below, except:
  // - this accepts and relies on a fetch result cache
  // but:
  // - this short-circuits Resolve.task / Fetch.task
  // - this doesn't support class loader hierarchies (all JARs are loaded by a single class loader)
  // - this doesn't set a '*.version' Java property with the version of the main dependency
  def fetchCacheTask(
    params: LaunchParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    userArgs: Seq[String]
  ): Task[(String, () => Option[Int])] = {

    val logger = params.shared.resolve.output.logger()
    val cache  = params.shared.resolve.cache.cache(pool, logger)

    for {
      depsAndReposOrError0 <-
        Task.fromEither(Resolve.depsAndReposOrError(params.shared.resolve, dependencyArgs, cache))
      (deps0, repositories, scalaVersionOpt, _) = depsAndReposOrError0
      files <- {
        val params0 = params.shared.resolve.copy(
          resolution = params.shared.resolve.updatedResolution(scalaVersionOpt)
        )
        coursier.Fetch(cache)
          .withDependencies(deps0)
          .withRepositories(repositories)
          .withResolutionParams(params0.resolution)
          .withCache(cache)
          .withFetchCache(params.fetchCacheIKnowWhatImDoing.map(new File(_)))
          .ioResult
          .map(_.artifacts)
      }
      javaPathEnvUpdate <- params.javaPath(cache)
      (javaPath, envUpdate) = javaPathEnvUpdate
      mainClass0 <- mainClass(params.shared, files.map(_._2), deps0.headOption)
      f <- Task.fromEither {
        launchCall(
          params,
          javaPath,
          mainClass0,
          files.map(_._2),
          Seq((None, files.map { case (a, f) => (Option(a), f) }.toArray)),
          params.shared.properties,
          envUpdate,
          userArgs,
          cache
        )
      }
    } yield (mainClass0, f)
  }

  def extraVersionProperty(res: Resolution, dependencyArgs: Seq[String]) = {

    // flaky-ness ahead: the install command passes the retained version of the first app dependency
    // as a Java property, e.g. sys.props("foo.version") = "0.1.2" for a dependency org::foo:0.1+ (note
    // that the scala suffix isn't added to the java prop name). That allows properties defined in the
    // app descriptor to reference that version (via property expansion, like ${foo.version} to get
    // the version here).
    // We don't have the actual app descriptor here, so we just pick the first dependency. It'll correspond
    // to the first app dependency only if the app is specified first.
    val nameModVerOpt = dependencyArgs
      .headOption
      .flatMap(DependencyParser.javaOrScalaDependencyParams(_).toOption)
      .map(_._1)
      .flatMap {
        case j: JavaOrScalaDependency.JavaDependency =>
          Some((j.module.module.name.value, j.dependency.moduleVersionConstraint))
        case s: JavaOrScalaDependency.ScalaDependency =>
          res.rootDependencies.headOption.filter(dep =>
            dep.module.organization == s.baseDependency.module.organization &&
            dep.module.name.value.startsWith(s.baseDependency.module.name.value) &&
            dep.versionConstraint == s.baseDependency.versionConstraint
          ).map { dep =>
            (s.baseDependency.module.name.value, dep.moduleVersionConstraint)
          }
      }

    for {
      (name, modVer) <- nameModVerOpt
    } yield {
      val v = res
        .projectCache0
        .get(modVer)
        .map(_._2.actualVersion0.asString)
        .getOrElse(modVer._2.asString)
      s"$name.version" -> v
    }
  }

  def task(
    params: LaunchParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    userArgs: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(String, () => Option[Int])] =
    for {
      t <- Fetch.task(params.shared.fetch(params.channel), pool, dependencyArgs, stdout, stderr)
      (res, scalaVersionOpt, platformOpt, files) = t
      mainClass0 <- mainClass(params.shared, files.map(_._2), res.rootDependencies.headOption)
      props = extraVersionProperty(res, dependencyArgs).toSeq ++ params.shared.properties
      cache = params.shared.resolve.cache.cache(
        pool,
        params.shared.resolve.output.logger()
      )
      javaPathEnvUpdate <- params.javaPath(cache)
      (javaPath, envUpdate) = javaPathEnvUpdate
      f <- Task.fromEither {
        launchCall(
          params,
          javaPath,
          mainClass0,
          files.map(_._2),
          loaderHierarchy(
            res,
            files,
            scalaVersionOpt,
            platformOpt,
            params.shared.sharedLoader,
            params.shared.artifact,
            Nil,
            params.shared.resolve.classpathOrder.getOrElse(true)
          ),
          props,
          envUpdate,
          userArgs,
          cache
        )
      }
    } yield (mainClass0, f)

  override def group: String = CommandGroup.launcher

  def run(options: LaunchOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) =
      LaunchParams(options).toEither.toOption.fold((options, args.remaining)) { initialParams =>
        val initialRepositories = initialParams.shared.resolve.repositories.repositories
        val channels            = initialParams.channel.channels
        pool = Sync.fixedThreadPool(initialParams.shared.resolve.cache.parallel)
        val cache = initialParams.shared.resolve.cache.cache(
          pool,
          initialParams.shared.resolve.output.logger()
        )
        val channels0 = Channels(channels, initialRepositories, cache)
        val res       = Resolve.handleApps(options, args.remaining, channels0)(_.addApp(_))

        if (options.json) {
          val app  = res._1.app
          val app0 = app.withDependencies((res._2 ++ app.dependencies).toList)
          println(RawAppDescriptor.encoder(app0).spaces2)
          sys.exit(0)
        }

        res
      }

    val params = LaunchParams(options0).exitOnError()

    if (pool == null)
      pool = Sync.fixedThreadPool(params.shared.resolve.cache.parallel)

    val ec = ExecutionContext.fromExecutorService(pool)

    val t =
      if (params.fetchCacheIKnowWhatImDoing.isEmpty)
        task(params, pool, deps, args.unparsed)
      else
        fetchCacheTask(params, pool, deps, args.unparsed)

    val (mainClass, run) =
      try t.unsafeRun(wrapExceptions = true)(ec)
      catch {
        case e: ResolveException if params.shared.resolve.output.verbosity <= 1 =>
          System.err.println(e.message)
          sys.exit(1)
        case e: coursier.error.FetchError if params.shared.resolve.output.verbosity <= 1 =>
          System.err.println(e.getMessage)
          sys.exit(1)
        case e: LaunchException.NoMainClassFound if params.shared.resolve.output.verbosity <= 1 =>
          System.err.println("Cannot find default main class. Specify one with -M or --main-class.")
          sys.exit(1)
        case e: LaunchException if params.shared.resolve.output.verbosity <= 1 =>
          System.err.println(e.getMessage)
          sys.exit(1)
      }

    if (params.shared.resolve.output.verbosity >= 2)
      System.err.println(s"Launching $mainClass ${args.unparsed.mkString(" ")}")
    else if (params.shared.resolve.output.verbosity == 1)
      System.err.println("Launching")

    run() match {
      case None =>
      case Some(retCode) =>
        if (retCode != 0)
          sys.exit(retCode)
    }
  }

}
