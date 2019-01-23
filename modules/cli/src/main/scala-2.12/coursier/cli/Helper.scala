package coursier
package cli

import java.io.{File, OutputStreamWriter, PrintWriter}
import java.net.{URL, URLClassLoader, URLDecoder}
import java.util.jar.{Manifest => JManifest}

import coursier.cache.{CacheDefaults, LocalRepositories}
import coursier.cli.options.{CommonOptions, IsolatedLoaderOptions}
import coursier.cli.scaladex.Scaladex
import coursier.cli.util.{JsonElem, JsonPrintRequirement, JsonReport}
import coursier.core.{Classifier, Type}
import coursier.extra.Typelevel
import coursier.ivy.IvyRepository
import coursier.util.Parse.ModuleRequirements
import coursier.util._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration


object Helper {
  def fileRepr(f: File) = f.toString

  def errPrintln(s: String) = Console.err.println(s)

  private val manifestPath = "META-INF/MANIFEST.MF"

  def mainClasses(cl: ClassLoader): Map[(String, String), String] = {
    import scala.collection.JavaConverters._

    val parentMetaInfs = Option(cl.getParent).fold(Set.empty[URL]) { parent =>
      parent.getResources(manifestPath).asScala.toSet
    }
    val allMetaInfs = cl.getResources(manifestPath).asScala.toVector

    val metaInfs = allMetaInfs.filterNot(parentMetaInfs)

    val mainClasses = metaInfs.flatMap { url =>
      val attributes = new JManifest(url.openStream()).getMainAttributes

      def attributeOpt(name: String) =
        Option(attributes.getValue(name))

      val vendor = attributeOpt("Implementation-Vendor-Id").getOrElse("")
      val title = attributeOpt("Specification-Title").getOrElse("")
      val mainClass = attributeOpt("Main-Class")

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }
}

class Helper(
  common: CommonOptions,
  rawDependencies: Seq[String],
  extraJars: Seq[File] = Nil,
  printResultStdout: Boolean = false,
  ignoreErrors: Boolean = false,
  isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  warnBaseLoaderNotFound: Boolean = true
) {
  import Helper.errPrintln
  import Util._
  import common._

  val ttl0 =
    if (common.cacheOptions.ttl.isEmpty)
      CacheDefaults.ttl
    else
      try Some(Duration(common.cacheOptions.ttl))
      catch {
        case e: Exception =>
          prematureExit(s"Unrecognized TTL duration: ${common.cacheOptions.ttl}")
      }

  val cachePolicies =
    if (common.cacheOptions.mode.isEmpty)
      CachePolicy.default
    else
      CacheParse.cachePolicies(common.cacheOptions.mode).either match {
        case Right(cp) => cp
        case Left(errors) =>
          prematureExit(
            s"Error parsing modes:\n${errors.map("  "+_).mkString("\n")}"
          )
      }

  val cache = new File(common.cacheOptions.cache)

  val pool = Schedulable.fixedThreadPool(common.cacheOptions.parallel)
  val ec = ExecutionContext.fromExecutorService(pool)

  val defaultRepositories = Seq(
    LocalRepositories.ivy2Local,
    Repositories.central
  ) ++ {
    if (common.dependencyOptions.sbtPlugin.isEmpty)
      Nil
    else
      Seq(
        Repositories.sbtPlugin("releases")
      )
  }

  val repositoriesValidation = CacheParse.repositories(common.repositoryOptions.repository).map { repos0 =>

    var repos = (if (common.repositoryOptions.noDefault) Nil else defaultRepositories) ++ repos0

    repos = repos.map {
      case m: MavenRepository => m.copy(sbtAttrStub = common.repositoryOptions.sbtPluginHack)
      case other => other
    }

    if (common.repositoryOptions.dropInfoAttr)
      repos = repos.map {
        case m: IvyRepository => m.copy(dropInfoAttributes = true)
        case other => other
      }

    repos
  }

  val standardRepositories = repositoriesValidation.either match {
    case Right(repos) =>
      repos
    case Left(errors) =>
      prematureExit(
        s"Error with repositories:\n${errors.map("  "+_).mkString("\n")}"
      )
  }

  val loggerFallbackMode =
    !common.outputOptions.progress && TermDisplay.defaultFallbackMode

  val scaladexDepsWithExtraParams: List[(Dependency, Map[String, String])] =
    if (common.dependencyOptions.scaladex.isEmpty)
      Nil
    else {
      val logger =
        if (common.verbosityLevel >= 0)
          Some(new TermDisplay(
            new OutputStreamWriter(System.err),
            fallbackMode = loggerFallbackMode
          ))
        else
          None

      val fetch = Cache.fetch[Task](
        cache,
        cachePolicies,
        checksums = Nil,
        logger = logger,
        pool = pool,
        ttl = ttl0,
        followHttpToHttpsRedirections = common.cacheOptions.followHttpToHttpsRedirect
      )

      logger.foreach(_.init())

      val scaladex = Scaladex.withCache(fetch)

      val res = Gather[Task].gather(common.dependencyOptions.scaladex.map { s =>
        val deps = scaladex.dependencies(
          s,
          common.dependencyOptions.scalaVersion,
          if (common.verbosityLevel >= 2) Console.err.println(_) else _ => ()
        )

        deps.map { modVers =>
          val m = modVers.groupBy(_._2)
          if (m.size > 1) {
            val (keptVer, modVers0) = m.map {
              case (v, l) =>
                val ver = coursier.core.Parse.version(v)
                  .getOrElse(???) // FIXME

              ver -> l
            }
            .maxBy(_._1)

            if (common.verbosityLevel >= 1)
              Console.err.println(s"Keeping version ${keptVer.repr}")

            modVers0
          } else
            modVers
        }.run
      }).unsafeRun()(ec)

      logger.foreach(_.stop())

      val errors = res.collect { case Left(err) => err }

      prematureExitIf(errors.nonEmpty) {
        s"Error getting scaladex infos:\n" + errors.map("  " + _).mkString("\n")
      }

      res
        .collect { case Right(l) => l }
        .flatten
        .map { case (mod, ver) => (Dependency(mod, ver), Map[String, String]()) }
        .toList
    }

  val (forceVersionErrors, forceVersions0) = Parse.moduleVersions(common.resolutionOptions.forceVersion, common.dependencyOptions.scalaVersion)

  prematureExitIf(forceVersionErrors.nonEmpty) {
    s"Cannot parse forced versions:\n" + forceVersionErrors.map("  "+_).mkString("\n")
  }

  val forceVersions = {
    val grouped = forceVersions0
      .groupBy { case (mod, _) => mod }
      .map { case (mod, l) => mod -> l.map { case (_, version) => version } }

    for ((mod, forcedVersions) <- grouped if forcedVersions.distinct.lengthCompare(1) > 0)
      errPrintln(s"Warning: version of $mod forced several times, using only the last one (${forcedVersions.last})")

    grouped.map { case (mod, versions) => mod -> versions.last }
  }

  val (excludeErrors, excludes0) = Parse.modules(common.dependencyOptions.exclude, common.dependencyOptions.scalaVersion)

  prematureExitIf(excludeErrors.nonEmpty) {
    s"Cannot parse excluded modules:\n" +
    excludeErrors
      .map("  " + _)
      .mkString("\n")
  }

  val (excludesNoAttr, excludesWithAttr) = excludes0.partition(_.attributes.isEmpty)

  prematureExitIf(excludesWithAttr.nonEmpty) {
    s"Excluded modules with attributes not supported:\n" +
      excludesWithAttr
        .map("  " + _)
        .mkString("\n")
  }

  val globalExcludes: Set[(Organization, ModuleName)] =
    excludesNoAttr
      .map(mod =>  (mod.organization, mod.name))
      .toSet

  val localExcludeMap: Map[String, Set[(Organization, ModuleName)]] =
    if (common.dependencyOptions.localExcludeFile.isEmpty) {
      Map()
    } else {
      val source = scala.io.Source.fromFile(common.dependencyOptions.localExcludeFile)
      val lines = try source.mkString.split("\n") finally source.close()

      lines
        .map { str =>
          val parent_and_child = str.split("--")
          if (parent_and_child.length != 2)
            throw new SoftExcludeParsingException(s"Failed to parse $str")

          val child_org_name = parent_and_child(1).split(":")
          if (child_org_name.length != 2)
            throw new SoftExcludeParsingException(s"Failed to parse $child_org_name")

          (parent_and_child(0), (Organization(child_org_name(0)), ModuleName(child_org_name(1))))
        }
        .groupBy(_._1)
        .mapValues(_.map(_._2).toSet)
        .iterator
        .toMap
    }

  val moduleReq = ModuleRequirements(globalExcludes, localExcludeMap, common.dependencyOptions.defaultConfiguration0)

  val (modVerCfgErrors: Seq[String], normalDepsWithExtraParams: Seq[(Dependency, Map[String, String])]) =
    Parse.moduleVersionConfigs(rawDependencies, moduleReq, transitive=true, common.dependencyOptions.scalaVersion)

  val (intransitiveModVerCfgErrors: Seq[String], intransitiveDepsWithExtraParams: Seq[(Dependency, Map[String, String])]) =
    Parse.moduleVersionConfigs(common.dependencyOptions.intransitive, moduleReq, transitive=false, common.dependencyOptions.scalaVersion)

  val (sbtPluginModVerCfgErrors: Seq[String], sbtPluginDepsWithExtraParams: Seq[(Dependency, Map[String, String])]) = {

    lazy val defaults = {
      val sbtVer = common.dependencyOptions.sbtVersion.split('.') match {
        case Array("1", _, _) =>
          // all sbt 1.x versions use 1.0 as short version
          "1.0"
        case arr => arr.take(2).mkString(".")
      }
      Map(
        "scalaVersion" -> common.dependencyOptions.scalaVersion.split('.').take(2).mkString("."),
        "sbtVersion" -> sbtVer
      )
    }
    val (errors, ok) = Parse.moduleVersionConfigs(common.dependencyOptions.sbtPlugin, moduleReq, transitive = true, common.dependencyOptions.scalaVersion)
    val ok0 = ok.map {
      case (dep, params) =>
        val dep0 = dep.copy(
          module = dep.module.copy(
            attributes = defaults ++ dep.module.attributes // dependency specific attributes override the default values
          )
        )
        (dep0, params)
    }
    (errors, ok0)
  }

  prematureExitIf(modVerCfgErrors.nonEmpty) {
    s"Cannot parse dependencies:\n" + modVerCfgErrors.map("  "+_).mkString("\n")
  }

  prematureExitIf(intransitiveModVerCfgErrors.nonEmpty) {
    s"Cannot parse intransitive dependencies:\n" +
      intransitiveModVerCfgErrors.map("  "+_).mkString("\n")
  }

  prematureExitIf(sbtPluginModVerCfgErrors.nonEmpty) {
    s"Cannot parse sbt plugin dependencies:\n" +
      sbtPluginModVerCfgErrors.map("  "+_).mkString("\n")
  }

  val transitiveDepsWithExtraParams: Seq[(Dependency, Map[String, String])] =
  // FIXME Order of the dependencies is not respected here (scaladex ones go first)
    scaladexDepsWithExtraParams ++ normalDepsWithExtraParams

  val transitiveDeps: Seq[Dependency] = transitiveDepsWithExtraParams.map(dep => dep._1)

  val allDependenciesWithExtraParams: Seq[(Dependency, Map[String, String])] =
    transitiveDepsWithExtraParams ++ intransitiveDepsWithExtraParams ++ sbtPluginDepsWithExtraParams

  val allDependencies: Seq[Dependency] = allDependenciesWithExtraParams.map(dep => dep._1)

  // Any dependencies with URIs should not be resolved with a pom so this is a
  // hack to add all the deps with URIs to the FallbackDependenciesRepository
  // which will be used during the resolve
  val depsWithUrls: Map[(Module, String), (URL, Boolean)] = allDependenciesWithExtraParams
    .flatMap {
      case (dep, extraParams) =>
        extraParams.get("url").map { url =>
          dep.moduleVersion -> (new URL(URLDecoder.decode(url, "UTF-8")), true)
        }
    }.toMap

  val depsWithUrlRepo = FallbackDependenciesRepository(depsWithUrls, common.cacheOptions.cacheFileArtifacts)

  // Prepend FallbackDependenciesRepository to the repository list
  // so that dependencies with URIs are resolved against this repo
  val repositories: Seq[Repository] = Seq(depsWithUrlRepo) ++ standardRepositories

  for (((mod, version), _) <- depsWithUrls if forceVersions.get(mod).exists(_ != version))
    throw new Exception(s"Cannot force a version that is different from the one specified " +
      s"for the module $mod:$version with url")

  val checksums = {
    val splitChecksumArgs = common.cacheOptions.checksum.flatMap(_.split(',')).filter(_.nonEmpty)
    if (splitChecksumArgs.isEmpty)
      CacheDefaults.checksums
    else
      splitChecksumArgs.map {
        case none if none.toLowerCase == "none" => None
        case sumType => Some(sumType)
      }
  }

  val userEnabledProfiles = common.resolutionOptions.profile.toSet

  val forcedProperties = common.resolutionOptions.forceProperty
    .map { s =>
      s.split("=", 2) match {
        case Array(k, v) => k -> v
        case _ =>
          sys.error(s"Malformed forced property argument: $s")
      }
    }
    .toMap

  val startRes = Resolution(
    allDependencies.toSet,
    forceVersions = forceVersions,
    filter = Some(dep => common.resolutionOptions.keepOptional || !dep.optional),
    userActivations =
      if (userEnabledProfiles.isEmpty) None
      else Some(userEnabledProfiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap),
    mapDependencies = if (common.resolutionOptions.typelevel) Some(Typelevel.swap(_)) else None,
    forceProperties = forcedProperties
  )

  val logger =
    if (common.verbosityLevel >= 0)
      Some(new TermDisplay(
        new OutputStreamWriter(System.err),
        fallbackMode = loggerFallbackMode
      ))
    else
      None

  val fetch = Cache.fetch[Task](
    cache,
    cachePolicies,
    checksums = checksums,
    logger = logger,
    pool = pool,
    ttl = ttl0,
    followHttpToHttpsRedirections = common.cacheOptions.followHttpToHttpsRedirect
  )
  val fetchQuiet = coursier.Fetch.from(repositories, fetch)
  val fetch0 =
    if (common.verbosityLevel >= 2) {
      modVers: Seq[(Module, String)] =>
        val print = Task.delay {
          errPrintln(s"Getting ${modVers.length} project definition(s)")
        }

        print.flatMap(_ => fetchQuiet(modVers))
    } else
      fetchQuiet

  if (common.verbosityLevel >= 1) {
    errPrintln(
      s"  Dependencies:\n" +
        Print.dependenciesUnknownConfigs(
          allDependencies,
          Map.empty,
          printExclusions = common.verbosityLevel >= 2
        )
    )

    if (forceVersions.nonEmpty) {
      errPrintln("  Force versions:")
      for ((mod, ver) <- forceVersions.toVector.sortBy { case (mod, _) => mod.toString })
        errPrintln(s"$mod:$ver")
    }
  }

  logger.foreach(_.init())

  val res =
    if (benchmark > 0) {
      class Counter(var value: Int = 0) {
        def add(value: Int): Unit = {
          this.value += value
        }
      }

      def timed[T](name: String, counter: Counter, f: Task[T]): Task[T] =
        Task.delay(System.currentTimeMillis()).flatMap { start =>
          f.map { t =>
            val end = System.currentTimeMillis()
            Console.err.println(s"$name: ${end - start} ms")
            counter.add((end - start).toInt)
            t
          }
        }

      def helper(proc: ResolutionProcess, counter: Counter, iteration: Int): Task[Resolution] =
        if (iteration >= common.resolutionOptions.maxIterations)
          Task.point(proc.current)
        else
          proc match {
            case _: core.Done =>
              Task.point(proc.current)
            case _ =>
              val iterationType = proc match {
                case _: core.Missing  => "IO"
                case _: core.Continue => "calculations"
                case _ => ???
              }

              timed(
                s"Iteration ${iteration + 1} ($iterationType)",
                counter,
                proc.next(fetch0, fastForward = false)).flatMap(helper(_, counter, iteration + 1)
              )
          }

      def res = {
        val iterationCounter = new Counter
        val resolutionCounter = new Counter

        val res0 = timed(
          "Resolution",
          resolutionCounter,
          helper(
            startRes.process,
            iterationCounter,
            0
          )
        ).unsafeRun()(ec)

        Console.err.println(s"Overhead: ${resolutionCounter.value - iterationCounter.value} ms")

        res0
      }

      @tailrec
      def result(warmUp: Int): Resolution =
        if (warmUp >= benchmark) {
          Console.err.println("Benchmark resolution")
          res
        } else {
          Console.err.println(s"Warm-up ${warmUp + 1} / $benchmark")
          res
          result(warmUp + 1)
        }

      result(0)
    } else if (benchmark < 0) {

      def res(index: Int) = {
        val start = System.currentTimeMillis()
        val res0 = startRes
          .process
          .run(fetch0, common.resolutionOptions.maxIterations)
          .unsafeRun()(ec)
        val end = System.currentTimeMillis()

        Console.err.println(s"Resolution ${index + 1} / ${-benchmark}: ${end - start} ms")

        res0
      }

      @tailrec
      def result(warmUp: Int): Resolution =
        if (warmUp >= -benchmark) {
          Console.err.println("Benchmark resolution")
          res(warmUp)
        } else {
          Console.err.println(s"Warm-up ${warmUp + 1} / ${-benchmark}")
          res(warmUp)
          result(warmUp + 1)
        }

      result(0)
    } else
      startRes
        .process
        .run(fetch0, common.resolutionOptions.maxIterations)
        .unsafeRun()(ec)

  logger.foreach(_.stop())

  val trDeps = res.minDependencies.toVector

  lazy val projCache = res.projectCache.mapValues { case (_, p) => p }

  if (printResultStdout || common.verbosityLevel >= 1 || tree || reverseTree) {
    if ((printResultStdout && common.verbosityLevel >= 1) || common.verbosityLevel >= 2 || tree || reverseTree)
      errPrintln(s"  Result:")

    val depsStr =
      if (reverseTree || tree)
        Print.dependencyTree(
          allDependencies,
          res,
          printExclusions = common.verbosityLevel >= 1,
          reverse = reverseTree
        )
      else
        Print.dependenciesUnknownConfigs(
          trDeps,
          projCache,
          printExclusions = common.verbosityLevel >= 1
        )

    if (printResultStdout)
      println(depsStr)
    else
      errPrintln(depsStr)
  }

  var anyError = false

  if (!res.isDone) {
    anyError = true
    errPrintln("\nMaximum number of iterations reached!")
  }

  if (res.errors.nonEmpty) {
    anyError = true
    errPrintln(
      "\nError:\n" +
      res.errors.map {
        case ((module, version), errors) =>
          s"  $module:$version\n${errors.map("    " + _.replace("\n", "    \n")).mkString("\n")}"
      }.mkString("\n")
    )
  }

  if (res.conflicts.nonEmpty) {
    anyError = true
    errPrintln(
      s"\nConflict:\n" +
      Print.dependenciesUnknownConfigs(
        res.conflicts.toVector,
        projCache,
        printExclusions = common.verbosityLevel >= 1
      )
    )
  }

  if (anyError) {
    if (ignoreErrors)
      errPrintln("Ignoring errors")
    else
      sys.exit(1)
  }

  def artifacts(
    sources: Boolean,
    javadoc: Boolean,
    default: Boolean,
    artifactTypes: Set[Type],
    classifier0: Set[Classifier],
    subset: Set[Dependency] = null
  ): Seq[Artifact] = {

    if (subset == null && common.verbosityLevel >= 1) {
      def isLocal(p: CachePolicy) = p match {
        case CachePolicy.LocalOnly => true
        case CachePolicy.LocalUpdate => true
        case CachePolicy.LocalUpdateChanging => true
        case _ => false
      }

      val msg =
        if (cachePolicies.forall(isLocal))
          "  Checking artifacts"
        else
          "  Fetching artifacts"

      errPrintln(msg)
    }

    val res0 = Option(subset).fold(res)(res.subset)

    val artifacts0 = getDepArtifactsForClassifier(sources, javadoc, default, classifier0, res0).map(t => (t._2, t._3))

    if (artifactTypes(Type("*")))
      artifacts0.map(_._2)
    else
      artifacts0.collect {
        case (attr, artifact) if artifactTypes(attr.`type`) =>
          artifact
      }
  }

  private def getDepArtifactsForClassifier(
    sources: Boolean,
    javadoc: Boolean,
    default: Boolean,
    classifier0: Set[Classifier],
    res0: Resolution
  ): Seq[(Dependency, Attributes, Artifact)] = {

    val raw =
      if (hasOverrideClassifiers(sources, javadoc, classifier0)) {
        val classifiers = overrideClassifiers(sources, javadoc, default, classifier0)

        val baseArtifacts =
          if (classifiers(Classifier("_")))
            res0.dependencyArtifacts(None)
          else
            Nil

        val classifierArtifacts =
          res0.dependencyArtifacts(Some(classifiers.filter(_ != Classifier("_")).toVector.sorted))

        baseArtifacts ++ classifierArtifacts
      } else
        res0.dependencyArtifacts(None)

    raw.map {
      case (dep, attr, artifact) =>
        (
          dep.copy(
            attributes = dep.attributes.copy(classifier = attr.classifier)),
          attr,
          artifact
        )
    }
  }

  private def overrideClassifiers(
    sources: Boolean,
    javadoc: Boolean,
    default: Boolean,
    classifier0: Set[Classifier]
  ): Set[Classifier] = {
    var classifiers = classifier0
    if (sources)
      classifiers = classifiers + Classifier.sources
    if (javadoc)
      classifiers = classifiers + Classifier.javadoc
    if (default)
      classifiers = classifiers + Classifier("_")
    classifiers
  }

  private def hasOverrideClassifiers(sources: Boolean, javadoc: Boolean, classifier0: Set[Classifier]): Boolean = {
    classifier0.nonEmpty || sources || javadoc
  }

  def fetchMap(
    sources: Boolean,
    javadoc: Boolean,
    default: Boolean,
    classifier0: Set[Classifier],
    artifactTypes: Set[Type],
    subset: Set[Dependency] = null
  ): Map[String, File] = {

    val artifacts0 = artifacts(sources, javadoc, default, artifactTypes, classifier0, subset).distinct

    val logger =
      if (common.verbosityLevel >= 0)
        Some(new TermDisplay(
          new OutputStreamWriter(System.err),
          fallbackMode = loggerFallbackMode
        ))
      else
        None

    if (common.verbosityLevel >= 1 && artifacts0.nonEmpty)
      println(s"  Found ${artifacts0.length} artifacts")

    val tasks = artifacts0.map { artifact =>
      val file0 = Cache.file[Task](
        artifact,
        cache,
        cachePolicies,
        checksums = checksums,
        logger = logger,
        pool = pool,
        ttl = ttl0,
        retry = common.cacheOptions.retryCount,
        localArtifactsShouldBeCached = common.cacheOptions.cacheFileArtifacts,
        followHttpToHttpsRedirections = common.cacheOptions.followHttpToHttpsRedirect
      )

      file0
        .run
        .map(artifact.->)
    }

    logger.foreach(_.init())

    val task = Task.gather.gather(tasks)

    val results = task.unsafeRun()(ec)

    val (ignoredErrors, errors) = results
      .collect {
        case (artifact, Left(err)) =>
          artifact -> err
      }
      .partition {
        case (a, err) =>
          val notFound = err match {
            case _: FileError.NotFound => true
            case _ => false
          }
          a.optional && notFound
      }

    val artifactToFile = results.collect {
      case (artifact: Artifact, Right(f)) =>
        (artifact.url, f)
    }.toMap

    logger.foreach(_.stop())

    if (common.verbosityLevel >= 2)
      errPrintln(
        "  Ignoring error(s):\n" +
        ignoredErrors
          .map {
            case (artifact, error) =>
              s"${artifact.url}: $error"
          }
          .mkString("\n")
      )

    exitIf(errors.nonEmpty) {
      s"  Error:\n" +
      errors
        .map {
          case (artifact, error) =>
            s"${artifact.url}: $error"
        }
        .mkString("\n")
    }

    val depToArtifacts: Map[Dependency, Vector[(Attributes, Artifact)]] =
      getDepArtifactsForClassifier(sources, javadoc, default, classifier0, res).groupBy(_._1).mapValues(_.map(t => (t._2, t._3)).toVector)


    if (!jsonOutputFile.isEmpty) {
      // TODO(wisechengyi): This is not exactly the root dependencies we are asking for on the command line, but it should be
      // a strict super set.
      val deps: Seq[Dependency] = Set(getDepArtifactsForClassifier(sources, javadoc, default, classifier0, res).map(_._1): _*).toSeq

      // A map from requested org:name:version to reconciled org:name:version
      val conflictResolutionForRoots: Map[String, String] = allDependencies.map({ dep =>
        val reconciledVersion: String = res.reconciledVersions
          .getOrElse(dep.module, dep.version)
        if (reconciledVersion != dep.version) {
          Option((s"${dep.module}:${dep.version}", s"${dep.module}:$reconciledVersion"))
        }
        else {
          Option.empty
        }
      }).filter(_.isDefined).map(_.get).toMap

      val artifacts: Seq[(Dependency, Artifact)] = res.dependencyArtifacts().map {
        case (dep, _, artifact) => (dep, artifact)
      }

      val jsonReq = JsonPrintRequirement(artifactToFile, depToArtifacts)
      val roots = deps.toVector.map(d =>
        JsonElem(
          d,
          artifacts,
          Option(jsonReq),
          res,
          printExclusions = common.verbosityLevel >= 1,
          excluded = false,
          colors = false,
          overrideClassifiers = overrideClassifiers(sources, javadoc, default, classifier0)
        )
      )
      val jsonStr = JsonReport(
        roots,
        conflictResolutionForRoots
      )(
        _.children,
        _.reconciledVersionStr,
        _.requestedVersionStr,
        _.downloadedFile
      )
      val pw = new PrintWriter(new File(jsonOutputFile))
      pw.write(jsonStr)
      pw.close()
    }
    artifactToFile
  }

  def fetch(
    sources: Boolean,
    javadoc: Boolean,
    default: Boolean,
    artifactTypes: Set[Type],
    classifier0: Set[Classifier],
    subset: Set[Dependency] = null
  ): Seq[File] =
    fetchMap(sources, javadoc, default, classifier0, artifactTypes, subset).values.toSeq

  def baseLoader = {

    @tailrec
    def rootLoader(cl: ClassLoader): ClassLoader =
      Option(cl.getParent) match {
        case Some(par) => rootLoader(par)
        case None => cl
      }

    rootLoader(ClassLoader.getSystemClassLoader)
  }

  lazy val (parentLoader, filteredFiles) = {

    // FIXME That shouldn't be hard-coded this way...
    // This whole class ought to be rewritten more cleanly.
    val artifactTypes = core.Resolution.defaultTypes

    val files0 = fetch(
      sources = false,
      javadoc = false,
      default = true,
      artifactTypes = artifactTypes,
      classifier0 = Set.empty
    )

    if (isolated.isolated.isEmpty)
      (baseLoader, files0)
    else {

      val isolatedDeps = isolated.isolatedDeps(common.dependencyOptions.scalaVersion)

      val (isolatedLoader, filteredFiles0) = isolated.targets.foldLeft((baseLoader, files0)) {
        case ((parent, files0), target) =>

          // FIXME These were already fetched above
          val isolatedFiles = fetch(
            sources = false,
            javadoc = false,
            default = true,
            artifactTypes = artifactTypes,
            classifier0 = Set.empty,
            subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
          )

          if (common.verbosityLevel >= 2) {
            Console.err.println(s"Isolated loader files:")
            for (f <- isolatedFiles.map(_.toString).sorted)
              Console.err.println(s"  $f")
          }

          val isolatedLoader = new URLClassLoader(isolatedFiles.map(_.toURI.toURL).toArray, parent)

          val filteredFiles0 = files0.filterNot(isolatedFiles.toSet)

          (isolatedLoader, filteredFiles0)
      }

      if (common.verbosityLevel >= 2) {
        Console.err.println(s"Remaining files:")
        for (f <- filteredFiles0.map(_.toString).sorted)
          Console.err.println(s"  $f")
      }

      (isolatedLoader, filteredFiles0)
    }
  }

  lazy val loader = new URLClassLoader(
    (filteredFiles ++ extraJars).map(_.toURI.toURL).toArray,
    parentLoader
  )


  lazy val retainedMainClass = {

    val mainClasses = Helper.mainClasses(loader)

    if (common.verbosityLevel >= 2) {
      Console.err.println("Found main classes:")
      for (((vendor, title), mainClass) <- mainClasses)
        Console.err.println(s"  $mainClass (vendor: $vendor, title: $title)")
      Console.err.println("")
    }

    val mainClass =
      if (mainClasses.size == 1) {
        val (_, mainClass) = mainClasses.head
        mainClass
      } else {

        // TODO Move main class detection code to the coursier-extra module to come, add non regression tests for it
        // In particular, check the main class for scalafmt, scalafix, ammonite, ...

        // Trying to get the main class of the first artifact
        val mainClassOpt = for {
          dep: Dependency <- transitiveDeps.headOption
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
          dep: Dependency <- transitiveDeps.headOption
          module = dep.module
          orgMainClasses = mainClasses.collect {
            case ((org, name), mainClass)
              if org == module.organization.value =>
              mainClass
          }.toSet
          if orgMainClasses.size == 1
        } yield orgMainClasses.head

        mainClassOpt.orElse(sameOrgOnlyMainClassOpt).getOrElse {
          Helper.errPrintln(s"Cannot find default main class. Specify one with -M or --main.")
          sys.exit(255)
        }
      }

    mainClass
  }
}
