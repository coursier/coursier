package coursier.cli.launch

import java.io.{File, PrintStream}
import java.lang.reflect.Modifier
import java.net.{URL, URLClassLoader}
import java.nio.file.Path
import java.util.concurrent.ExecutorService

import caseapp.CaseApp
import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.fetch.Fetch
import coursier.cli.params.{ArtifactParams, SharedLaunchParams, SharedLoaderParams}
import coursier.cli.resolve.{Resolve, ResolveException}
import coursier.core.{Dependency, Resolution}
import coursier.error.ResolutionError
import coursier.install.{Channels, MainClass, RawAppDescriptor}
import coursier.parse.{DependencyParser, JavaOrScalaDependency, JavaOrScalaModule}
import coursier.paths.Jep
import coursier.util.{Artifact, Sync, Task}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

object Launch extends CaseApp[LaunchOptions] {

  def baseLoader: ClassLoader = {

    @tailrec
    def rootLoader(cl: ClassLoader): ClassLoader =
      Option(cl.getParent) match {
        case Some(par) => rootLoader(par)
        case None => cl
      }

    rootLoader(ClassLoader.getSystemClassLoader)
  }

  def launchFork(
    hierarchy: Seq[(Option[String], Array[File])],
    mainClass: String,
    args: Seq[String],
    javaOptions: Seq[String],
    properties: Seq[(String, String)],
    verbosity: Int
  ): Either[LaunchException, () => Int] =
    hierarchy match {
      case Seq((None, files)) =>

        // Read JAVA_HOME if it's set? Allow users to change that command via CLI options?
        val cmd = Seq("java") ++
          javaOptions ++
          properties.map { case (k, v) => s"-D$k=$v" } ++
          Seq("-cp", files.map(_.getAbsolutePath).mkString(File.pathSeparator), mainClass) ++
          args

        val b = new ProcessBuilder()
        b.command(cmd: _*)
        b.inheritIO()

        Right {
          () =>
            if (verbosity >= 1)
              System.err.println(s"Running ${cmd.map("\"" + _.replace("\"", "\\\"") + "\"").mkString(" ")}")
            val p = b.start()
            p.waitFor()
        }
      case _ =>
        Left(new LaunchException.ClasspathHierarchyUnsupportedWhenForking)
    }

  def launch(
    hierarchy: Seq[(Option[String], Array[Path])],
    mainClass: String,
    args: Seq[String],
    properties: Seq[(String, String)]
  ): Either[LaunchException, () => Unit] = {

    val loader0 = loader(
      hierarchy.map {
        case (nameOpt, files) =>
          (nameOpt, files.map(_.toUri.toURL))
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
        } catch {
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
        val currentThread = Thread.currentThread()
        val previousLoader = currentThread.getContextClassLoader
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
            case (k, None) => System.clearProperty(k)
            case (k, Some(v)) => System.setProperty(k, v)
          }
        }
    }
  }

  def loaderHierarchy(
    res: Resolution,
    files: Seq[(Artifact, Path)],
    scalaVersion: String,
    platformOpt: Option[String],
    sharedLoaderParams: SharedLoaderParams,
    artifactParams: ArtifactParams,
    extraJars: Seq[Path],
    classpathOrder: Boolean
  ): Seq[(Option[String], Array[Path])] = {
    val fileMap = files.toMap
    val alreadyAdded = Set.empty[Path] // unused???
    val parents = sharedLoaderParams.loaderNames.map { name =>
        val deps = sharedLoaderParams.loaderDependencies.getOrElse(name, Nil)
        val subRes = res.subset(deps.map(_.dependency(JavaOrScalaModule.scalaBinaryVersion(scalaVersion), scalaVersion, platformOpt.getOrElse(""))))
        val artifacts = coursier.Artifacts.artifacts0(
          subRes,
          artifactParams.classifiers,
          Option(artifactParams.mainArtifacts).map(x => x),
          Option(artifactParams.artifactTypes),
          classpathOrder
        ).map(_._3)
        val files0 = artifacts
          .map(a => fileMap.getOrElse(a, sys.error("should not happen")))
          .filter(!alreadyAdded(_))
        Some(name) -> files0.toArray
    }
    val cp = files.map(_._2).filterNot(alreadyAdded).toArray ++ extraJars
    parents :+ (None, cp)
  }

  def loader(hierarchy: Seq[(Option[String], Array[URL])]): ClassLoader =
    hierarchy.foldLeft(baseLoader) {
      case (parent, (None, urls)) =>
        new URLClassLoader(urls, parent)
      case (parent, (Some(name), urls)) =>
        new SharedClassLoader(urls, parent, Array(name))
    }

  def mainClass(params: SharedLaunchParams, files: Seq[Path], mainDependencyOpt: Option[Dependency]) =
    params.mainClassOpt match {
      case Some(c) =>
        Task.point(c)
      case None =>
        Task.delay(MainClass.mainClasses(files)).flatMap { m =>
          if (params.resolve.output.verbosity >= 2)
            System.err.println(
              "Found main classes:\n" +
                m.map { case ((vendor, title), mainClass) => s"  $mainClass (vendor: $vendor, title: $title)\n" }.mkString +
                "\n"
            )
          MainClass.retainedMainClassOpt(m, mainDependencyOpt.map(d => (d.module.organization.value, d.module.name.value))) match {
            case Some(c) =>
              Task.point(c)
            case None =>
              Task.fail(new LaunchException.NoMainClassFound)
          }
        }
    }

  def launchCall(
    params: LaunchParams,
    mainClass0: String,
    files: Seq[Path],
    hierarchy: Seq[(Option[String], Array[Path])],
    props: Seq[(String, String)],
    userArgs: Seq[String]
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

        (props, Some(jepJar.toPath))
      } else
        (Nil, None)

    val extraJars = params.shared.extraJars ++ jepExtraJar.toSeq
    val hierarchy0 =
      if (extraJars.isEmpty) hierarchy
      else {
        val (nameOpt, files0) = hierarchy.last
        hierarchy.init :+ ((nameOpt, files0 ++ extraJars))
      }

    val jcp =
      hierarchy0 match {
        case Seq((None, files)) =>
          Seq(
            "java.class.path" -> files.mkString(File.pathSeparator)
          )
        case _ =>
          Nil
      }

    val properties0 = {
      val m = new java.util.LinkedHashMap[String, String]
      // order matters - jcp first, so that it can be referenced from subsequent variables before expansion
      for ((k, v) <- jcp.iterator ++ jlp.iterator ++ props.iterator)
        m.put(k, v)
      val m0 = coursier.paths.Util.expandProperties(m)
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

    if (params.fork)
      launchFork(hierarchy0.map { case (nameOpt, files) => (nameOpt, files.map(_.toFile)) }, mainClass0, userArgs, params.javaOptions, properties0, params.shared.resolve.output.verbosity)
        .map(f => () => Some(f()))
    else
      launch(hierarchy0, mainClass0, userArgs, properties0)
        .map(f => { () => f(); None })
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
    val cache = params.shared.resolve.cache.cache[Task](pool, logger)

    for {
      depsAndReposOrError0 <- Task.fromEither(Resolve.depsAndReposOrError(params.shared.resolve, dependencyArgs, cache))
      (deps0, repositories, scalaVersion, _) = depsAndReposOrError0
      files <- {
        val params0 = params.shared.resolve.copy(
          resolution = params.shared.resolve.resolution
            .withScalaVersionOpt(params.shared.resolve.resolution.scalaVersionOpt.map(_ => scalaVersion))
        )
        coursier.Fetch(cache)
          .withDependencies(deps0)
          .withRepositories(repositories)
          .withResolutionParams(params0.resolution)
          .withCache(cache)
          .withFetchCache(params.fetchCacheIKnowWhatImDoing.map(new File(_)))
          .io
      }
      mainClass0 <- mainClass(params.shared, files, deps0.headOption)
      f <- Task.fromEither {
        launchCall(
          params,
          mainClass0,
          files,
          Seq((None, files.toArray)),
          params.shared.properties,
          userArgs
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
          Some((j.module.module.name.value, j.dependency.moduleVersion))
        case s: JavaOrScalaDependency.ScalaDependency =>
          res.rootDependencies.headOption.filter(dep =>
            dep.module.organization == s.baseDependency.module.organization &&
              dep.module.name.value.startsWith(s.baseDependency.module.name.value) &&
              dep.version == s.baseDependency.version
          ).map { dep =>
            (s.baseDependency.module.name.value, dep.moduleVersion)
          }
      }

    for {
      (name, modVer) <- nameModVerOpt
    } yield {
      val v = res
        .projectCache
        .get(modVer)
        .map(_._2.actualVersion)
        .getOrElse(modVer._2)
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
      t <- Fetch.task(params.shared.fetch, pool, dependencyArgs, stdout, stderr)
      (res, scalaVersion, platformOpt, files) = t
      mainClass0 <- mainClass(params.shared, files.map(_._2), res.rootDependencies.headOption)
      props = extraVersionProperty(res, dependencyArgs).toSeq ++ params.shared.properties
      f <- Task.fromEither {
        launchCall(
          params,
          mainClass0,
          files.map(_._2),
          loaderHierarchy(
            res,
            files,
            scalaVersion,
            platformOpt,
            params.shared.sharedLoader,
            params.shared.artifact,
            Nil,
            params.shared.resolve.classpathOrder
          ),
          props,
          userArgs
        )
      }
    } yield (mainClass0, f)

  def run(options: LaunchOptions, args: RemainingArgs): Unit = {

    var pool: ExecutorService = null

    // get options and dependencies from apps if any
    val (options0, deps) = LaunchParams(options).toEither.toOption.fold((options, args.remaining)) { initialParams =>
      val initialRepositories = initialParams.shared.resolve.repositories.repositories
      val channels = initialParams.shared.resolve.repositories.channels
      pool = Sync.fixedThreadPool(initialParams.shared.resolve.cache.parallel)
      val cache = initialParams.shared.resolve.cache.cache(pool, initialParams.shared.resolve.output.logger())
      val channels0 = Channels(channels, initialRepositories, cache)
      val res = Resolve.handleApps(options, args.remaining, channels0)(_.addApp(_))

      if (options.json) {
        val app = res._1.app
        val app0 = app.withDependencies((res._2 ++ app.dependencies).toList)
        println(RawAppDescriptor.encoder(app0).spaces2)
        sys.exit(0)
      }

      res
    }

    LaunchParams(options0) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        if (pool == null)
          pool = Sync.fixedThreadPool(params.shared.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t =
          if (params.fetchCacheIKnowWhatImDoing.isEmpty)
            task(params, pool, deps, args.unparsed)
          else
            fetchCacheTask(params, pool, deps, args.unparsed)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) if params.shared.resolve.output.verbosity <= 1 =>
            System.err.println(e.message)
            sys.exit(1)
          case Left(e: coursier.error.FetchError) if params.shared.resolve.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
          case Left(e: LaunchException.NoMainClassFound) if params.shared.resolve.output.verbosity <= 1 =>
            System.err.println("Cannot find default main class. Specify one with -M or --main-class.")
            sys.exit(1)
          case Left(e: LaunchException) if params.shared.resolve.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
          case Left(e) => throw e
          case Right((mainClass, run)) =>
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
  }

}
