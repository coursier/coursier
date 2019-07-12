package coursier.cli.launch

import java.io.{File, InputStream, PrintStream}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.ExecutorService
import java.util.jar.{Manifest => JManifest}
import java.util.zip.ZipFile

import caseapp.CaseApp
import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.app.RawAppDescriptor
import coursier.cli.fetch.Fetch
import coursier.cli.params.{ArtifactParams, SharedLoaderParams}
import coursier.cli.resolve.{Resolve, ResolveException}
import coursier.core.{Artifact, Dependency, Resolution}
import coursier.parse.{DependencyParser, JavaOrScalaDependency}
import coursier.util.{Sync, Task}

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

  def launch(
    loader: ClassLoader,
    mainClass: String,
    args: Seq[String],
    properties: Seq[(String, String)]
  ): Either[LaunchException, () => Unit] =
    for {
      cls <- {
        try Right(loader.loadClass(mainClass))
        catch {
          case e: ClassNotFoundException =>
            Left(new LaunchException.MainClassNotFound(mainClass, e))
          }
      }.right
      method <- {
        try {
          val m = cls.getMethod("main", classOf[Array[String]])
          m.setAccessible(true)
          Right(m)
        } catch {
          case e: NoSuchMethodException =>
            Left(new LaunchException.MainMethodNotFound(cls, e))
        }
      }.right
    } yield {
      () =>
        val properties0 = {
          val m = new java.util.LinkedHashMap[String, String]
          for ((k, v) <- properties)
            m.put(k, v)
          val m0 = coursier.paths.Util.expandProperties(m)
          val b = new ListBuffer[(String, String)]
          m0.forEach(
            new java.util.function.BiConsumer[String, String] {
              def accept(k: String, v: String) =
                b += k -> v
            }
          )
          b.result()
        }
        val currentThread = Thread.currentThread()
        val previousLoader = currentThread.getContextClassLoader
        val previousProperties = properties0.map(_._1).map(k => k -> Option(System.getProperty(k)))
        try {
          currentThread.setContextClassLoader(loader)
          for ((k, v) <- properties0)
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

  private def manifestPath = "META-INF/MANIFEST.MF"

  def mainClasses(jars: Seq[File]): Map[(String, String), String] = {

    val metaInfs = jars.flatMap { f =>
      val zf = new ZipFile(f)
      val entryOpt = Option(zf.getEntry(manifestPath))
      entryOpt.map(e => () => zf.getInputStream(e)).toSeq
    }

    val mainClasses = metaInfs.flatMap { f =>
      var is: InputStream = null
      val attributes =
        try {
          is = f()
          new JManifest(is).getMainAttributes
        } finally {
          if (is != null)
            is.close()
        }

      def attributeOpt(name: String) =
        Option(attributes.getValue(name))

      val vendor = attributeOpt("Implementation-Vendor-Id").getOrElse("")
      val title = attributeOpt("Specification-Title").getOrElse("")
      val mainClass = attributeOpt("Main-Class")

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }

  def retainedMainClassOpt(
    mainClasses: Map[(String, String), String],
    mainDependencyOpt: Option[Dependency]
  ): Option[String] =
    if (mainClasses.size == 1) {
      val (_, mainClass) = mainClasses.head
      Some(mainClass)
    } else {

      // Trying to get the main class of the first artifact
      val mainClassOpt = for {
        dep <- mainDependencyOpt
        module = dep.module
        mainClass <- mainClasses.collectFirst {
          case ((org, name), mainClass)
            if org == module.organization.value && (
              module.name.value == name ||
                module.name.value.startsWith(name + "_") // Ignore cross version suffix
              ) =>
            mainClass
        }
      } yield mainClass

      def sameOrgOnlyMainClassOpt = for {
        dep <- mainDependencyOpt
        module = dep.module
        orgMainClasses = mainClasses.collect {
          case ((org, _), mainClass)
            if org == module.organization.value =>
            mainClass
        }.toSet
        if orgMainClasses.size == 1
      } yield orgMainClasses.head

      mainClassOpt.orElse(sameOrgOnlyMainClassOpt)
    }

  def loader(
    res: Resolution,
    files: Seq[(Artifact, File)],
    sharedLoaderParams: SharedLoaderParams,
    artifactParams: ArtifactParams,
    extraJars: Seq[URL]
  ): URLClassLoader = {
    val fileMap = files.toMap
    val alreadyAdded = Set.empty[File] // unused???
    val parent = sharedLoaderParams.loaderNames.foldLeft(baseLoader) {
      (parent, name) =>
        val deps = sharedLoaderParams.loaderDependencies.getOrElse(name, Nil)
        val subRes = res.subset(deps)
        val artifacts = coursier.Artifacts.artifacts0(
          subRes,
          artifactParams.classifiers,
          Option(artifactParams.mainArtifacts).map(x => x),
          Option(artifactParams.artifactTypes)
        ).map(_._3)
        val files0 = artifacts
          .map(a => fileMap.getOrElse(a, sys.error("should not happen")))
          .filter(!alreadyAdded(_))
        new SharedClassLoader(files0.map(_.toURI.toURL).toArray, parent, Array(name))
    }
    val cp = files.map(_._2).filterNot(alreadyAdded).map(_.toURI.toURL).toArray ++ extraJars
    new URLClassLoader(cp, parent)
  }

  def task(
    params: LaunchParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    userArgs: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(String, () => Unit)] =
    for {
      t <- Fetch.task(params.shared.fetch, pool, dependencyArgs, stdout, stderr)
      (res, files) = t
      loader0 <- Task.delay {
        loader(
          res,
          files,
          params.shared.sharedLoader,
          params.shared.artifact,
          params.shared.extraJars.map(_.toUri.toURL)
        )
      }
      mainClass <- {
        params.shared.mainClassOpt match {
          case Some(c) =>
            Task.point(c)
          case None =>
            Task.delay(mainClasses(files.map(_._2))).flatMap { m =>
              if (params.shared.resolve.output.verbosity >= 2)
                System.err.println(
                  "Found main classes:\n" +
                    m.map { case ((vendor, title), mainClass) => s"  $mainClass (vendor: $vendor, title: $title)\n" }.mkString +
                    "\n"
                )
              retainedMainClassOpt(m, res.rootDependencies.headOption) match {
                case Some(c) =>
                  Task.point(c)
                case None =>
                  Task.fail(new LaunchException.NoMainClassFound)
              }
            }
        }
      }
      props = {

        // flaky-ness ahead: the install command passes the retained version of the first app dependency
        // as a Java property, e.g. sys.props("foo.version") = "0.1.2" for a dependency org::foo:0.1+ (note
        // that the scala suffix isn't added to the java prop name). That allows properties defined in the
        // app descriptor to reference that version (via property expansion, like ${foo.version} to get
        // the version here).
        // We don't have the actual app descriptor here, so we just pick the first dependency. It'll correspond
        // to the first app dependency only if the app is specified first.
        val nameModVerOpt = dependencyArgs
          .headOption
          .flatMap(DependencyParser.javaOrScalaDependencyParams(_).right.toOption)
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

        val opt = for {
          (name, modVer) <- nameModVerOpt
        } yield {
          val v = res
            .projectCache
            .get(modVer)
            .map(_._2.actualVersion)
            .getOrElse(modVer._2)
          s"$name.version" -> v
        }

        val jcp =
          if (params.shared.sharedLoader.loaderNames.isEmpty)
            Seq("java.class.path" -> files.map(_._2.getAbsolutePath).mkString(File.pathSeparator))
          else
            Nil

        // order matters: first set jcp, so that the expansion of subsequent properties can reference it
        jcp ++ opt.toSeq ++ params.shared.properties
      }
      f <- Task.fromEither(launch(loader0, mainClass, userArgs, props))
    } yield (mainClass, f)

  def run(options: LaunchOptions, args: RemainingArgs): Unit = {

    // get options and dependencies from apps if any
    val (options0, deps) = LaunchParams(options).toEither.toOption.fold((options, args.remaining)) { initialParams =>
      val initialRepositories = initialParams.shared.resolve.repositories.repositories
      val channels = initialParams.shared.resolve.repositories.channels
      val pool = Sync.fixedThreadPool(initialParams.shared.resolve.cache.parallel)
      val cache = initialParams.shared.resolve.cache.cache(pool, initialParams.shared.resolve.output.logger())
      val res = Resolve.handleApps(options, args.remaining, channels, initialRepositories, cache)(_.addApp(_))
      pool.shutdown()

      if (options.json) {
        val app = res._1.app
        val app0 = app.copy(
          dependencies = (res._2 ++ app.dependencies).toList
        )
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

        val pool = Sync.fixedThreadPool(params.shared.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, deps, args.unparsed)

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

            run()
        }
    }
  }

}
