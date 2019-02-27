package coursier.cli.launch

import java.io.{File, InputStream, PrintStream}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.ExecutorService
import java.util.jar.{Manifest => JManifest}

import caseapp.CaseApp
import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.fetch.Fetch
import coursier.cli.options.LaunchOptions
import coursier.cli.params.LaunchParams
import coursier.cli.resolve.ResolveException
import coursier.core.Dependency
import coursier.util.{Sync, Task}

import scala.annotation.tailrec
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
    args: Seq[String]
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
        Thread.currentThread().setContextClassLoader(loader)
        try method.invoke(null, args.toArray)
        catch {
          case e: java.lang.reflect.InvocationTargetException =>
            throw Option(e.getCause).getOrElse(e)
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

  def task(
    params: LaunchParams,
    pool: ExecutorService,
    dependencyArgs: Seq[String],
    userArgs: Seq[String],
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err
  ): Task[(String, () => Unit)] =
    for {
      t <- Fetch.task(params.fetch, pool, dependencyArgs, stdout, stderr)
      (res, files) = t
      fileMap = files.toMap
      loader <- Task.delay {
        val alreadyAdded = Set.empty[File]
        val parent = params.sharedLoader.loaderNames.foldLeft(baseLoader) {
          (parent, name) =>
            val deps = params.sharedLoader.loaderDependencies.getOrElse(name, Nil)
            val subRes = res.subset(deps)
            val artifacts = coursier.Artifacts.artifacts0(
              subRes,
              params.artifact.classifiers,
              params.artifact.mainArtifacts,
              params.artifact.artifactTypes
            ).map(_._3)
            val files0 = artifacts
              .map(a => fileMap.getOrElse(a, sys.error("should not happen")))
              .filter(!alreadyAdded(_))
            new SharedClassLoader(files0.map(_.toURI.toURL).toArray, parent, Array(name))
        }
        val cp = files.map(_._2).filterNot(alreadyAdded).map(_.toURI.toURL).toArray ++
          params.extraJars.map(_.toUri.toURL)
        new URLClassLoader(cp, parent)
      }
      mainClass <- {
        params.mainClassOpt match {
          case Some(c) =>
            Task.point(c)
          case None =>
            Task.delay(mainClasses(loader)).flatMap { m =>
              if (params.resolve.output.verbosity >= 2)
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
      f <- Task.fromEither(launch(loader, mainClass, userArgs))
    } yield (mainClass, f)

  def run(options: LaunchOptions, args: RemainingArgs): Unit =
    LaunchParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Validated.Valid(params) =>

        val pool = Sync.fixedThreadPool(params.resolve.cache.parallel)
        val ec = ExecutionContext.fromExecutorService(pool)

        val t = task(params, pool, args.remaining, args.unparsed)

        t.attempt.unsafeRun()(ec) match {
          case Left(e: ResolveException) if params.resolve.output.verbosity <= 1 =>
            System.err.println(e.message)
            sys.exit(1)
          case Left(e: coursier.error.FetchError) if params.resolve.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
          case Left(e: LaunchException.NoMainClassFound) if params.resolve.output.verbosity <= 1 =>
            System.err.println("Cannot find default main class. Specify one with -M or --main-class.")
            sys.exit(1)
          case Left(e: LaunchException) if params.resolve.output.verbosity <= 1 =>
            System.err.println(e.getMessage)
            sys.exit(1)
          case Left(e) => throw e
          case Right((mainClass, run)) =>
            if (params.resolve.output.verbosity >= 2)
              System.err.println(s"Launching $mainClass ${args.unparsed.mkString(" ")}")
            else if (params.resolve.output.verbosity == 1)
              System.err.println("Launching")

            run()
        }
    }

}
