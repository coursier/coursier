package coursier.cli.launch

import java.io.{File, InputStream, PrintStream}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.ExecutorService
import java.util.jar.{Manifest => JManifest}

import caseapp.CaseApp
import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.fetch.Fetch
import coursier.cli.params.{ArtifactParams, SharedLoaderParams}
import coursier.cli.resolve.ResolveException
import coursier.core.{Artifact, Dependency, Resolution}
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

  def mainClasses(cl: ClassLoader): Map[(String, String), String] = {
    import scala.collection.JavaConverters._

    val parentMetaInfs = Option(cl.getParent).fold(Set.empty[URL]) { parent =>
      parent.getResources(manifestPath).asScala.toSet
    }
    val allMetaInfs = cl.getResources(manifestPath).asScala.toVector

    val metaInfs = allMetaInfs.filterNot(parentMetaInfs)

    val mainClasses = metaInfs.flatMap { url =>
      var is: InputStream = null
      val attributes =
        try {
          is = url.openStream()
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
    val alreadyAdded = Set.empty[File]
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
            Task.delay(mainClasses(loader0)).flatMap { m =>
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
        if (params.shared.sharedLoader.loaderNames.isEmpty)
          Seq("java.class.path" -> files.map(_._2.getAbsolutePath).mkString(File.pathSeparator))
        else
          Nil
      }
      f <- Task.fromEither(launch(loader0, mainClass, userArgs, props))
    } yield (mainClass, f)

  def run(options: LaunchOptions, args: RemainingArgs): Unit =
    LaunchParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Sync.fixedThreadPool(params.shared.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, args.remaining, args.unparsed)

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
