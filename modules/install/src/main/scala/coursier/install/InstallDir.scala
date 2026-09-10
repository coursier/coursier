package coursier.install

import dataclass.{data, since => unroll}

import java.io.{File, InputStream, OutputStream}
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.time.Instant
import java.util.Locale
import java.util.stream.Stream
import java.util.zip.ZipEntry

import coursier.cache.{ArchiveCache, ArchiveType, Cache}
import coursier.core.{Dependency, Module, Repository}
import coursier.env.EnvironmentUpdate
import coursier.install.error._
import coursier.install.internal._
import coursier.launcher.{ClassLoaderContent, ClassPathEntry, Generator, Parameters, Preamble}
import coursier.launcher.internal.FileUtil
import coursier.launcher.Parameters.ScalaNative
import coursier.parse.JavaOrScalaModule
import coursier.util.{Artifact, Task}
import coursier.version.VersionConstraint

import java.util.regex.Pattern

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class InstallDir(
  baseDir: Path = InstallDir.defaultDir,
  @unroll
  cache: Cache[Task] = Cache.default,
  @unroll
  verbosity: Int = 0,
  graalvmParamsOpt: Option[GraalvmParams] = None,
  coursierRepositories: Seq[Repository] = Nil,
  platform: Option[String] = Platform.get(),
  platformExtensions: Seq[String] = InstallDir.platformExtensions(),
  @deprecated("ignored, use platform instead", "2.1.0-M4")
  os: String = System.getProperty("os.name", ""),
  nativeImageJavaHome: Option[String => Task[File]] = None,
  onlyPrebuilt: Boolean = false,
  preferPrebuilt: Boolean = true,
  basePreamble: Preamble = Preamble(),
  @unroll
  overrideProguardedBootstraps: Option[Boolean] = None,
  @unroll
  archiveCache: ArchiveCache[Task] = ArchiveCache()
) {

  private lazy val isWin = platform.exists(_.endsWith("-pc-win32"))
  private lazy val auxExtension =
    if (isWin) ".exe"
    else ""

  import InstallDir._

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo
  ): Option[Boolean] =
    createOrUpdate(appInfo, Instant.now(), force = false)

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo,
    currentTime: Instant
  ): Option[Boolean] =
    createOrUpdate(appInfo, currentTime, force = false)

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo,
    currentTime: Instant,
    force: Boolean
  ): Option[Boolean] = {

    val name = appInfo.appDescriptor.nameOpt
      .getOrElse(appInfo.source.id)
    val dest = baseDir.resolve(name)

    createOrUpdate(
      Some((appInfo.appDescriptor, appInfo.appDescriptorBytes)),
      Some(appInfo.sourceBytes),
      dest,
      currentTime,
      force
    )
  }

  def delete(appName: String): Option[Boolean] = {
    val launcher = actualDest(baseDir.resolve(appName))
    Updatable.delete(baseDir, launcher, auxExtension, verbosity)
  }

  private[install] def actualDest(name: String): Path =
    actualDest(baseDir.resolve(name))

  private def actualName(dest: Path): String = {
    val name = dest.getFileName.toString
    if (isWin) name.stripSuffix(".bat")
    else name
  }

  private def actualDest(dest: Path): Path =
    if (isWin) dest.getParent.resolve(dest.getFileName.toString + ".bat")
    else dest

  private def baseJarPreamble(desc: AppDescriptor): Preamble =
    basePreamble
      .withOsKind(isWin)
      .callsItself(isWin)
      .copy(javaOpts = desc.javaOptions, jvmOptionFile = desc.jvmOptionFile)

  private def bootstrapParamsLike(
    desc: AppDescriptor,
    appArtifacts: AppArtifacts,
    infoEntries: Seq[(ZipEntry, Array[Byte])],
    mainClass: String,
    baseJarPreamble: Preamble
  ): Parameters = {
    val isStandalone = desc.launcherType != LauncherType.Bootstrap
    val sharedContentOpt =
      if (appArtifacts.shared.isEmpty) None
      else {
        val entries = appArtifacts.shared.map {
          case (a, f) =>
            classpathEntry(a, f, forceResource = isStandalone)
        }

        Some(ClassLoaderContent(entries))
      }
    val mainContent = ClassLoaderContent(
      appArtifacts.fetchResult.artifacts.map {
        case (a, f) =>
          classpathEntry(a, f, forceResource = isStandalone)
      }
    )

    val params0 = Parameters.Bootstrap(sharedContentOpt.toSeq :+ mainContent, mainClass)
      .withPreamble(baseJarPreamble)
      .copy(
        javaProperties = desc.javaProperties ++ appArtifacts.extraProperties,
        deterministic = true,
        hybridAssembly = desc.launcherType == LauncherType.Hybrid,
        extraZipEntries = infoEntries,
        pythonJep = desc.jna.contains("python-jep"),
        python = desc.jna.contains("python")
      )

    overrideProguardedBootstraps
      .fold(params0)(pg => params0.copy(proguarded = pg))
  }

  private[install] def params(
    desc: AppDescriptor,
    appArtifacts: AppArtifacts,
    infoEntries: Seq[(ZipEntry, Array[Byte])],
    mainClass: String
  ): Parameters = {

    val baseJarPreamble0 = baseJarPreamble(desc)

    desc.launcherType match {
      case LauncherType.DummyJar =>
        Parameters.Bootstrap(Nil, mainClass)
          .withPreamble(baseJarPreamble0)
          .copy(
            javaProperties = desc.javaProperties ++ appArtifacts.extraProperties,
            deterministic = true,
            hybridAssembly = desc.launcherType == LauncherType.Hybrid,
            extraZipEntries = infoEntries
          )

      case _: LauncherType.BootstrapLike =>
        bootstrapParamsLike(desc, appArtifacts, infoEntries, mainClass, baseJarPreamble0)
      case LauncherType.Assembly =>
        assert(appArtifacts.shared.isEmpty) // just in case

        // FIXME Allow to adjust merge rules?
        Parameters.Assembly()
          .withPreamble(baseJarPreamble0)
          .copy(files = appArtifacts.fetchResult.files)
          .withMainClass(mainClass)
          .copy(extraZipEntries = infoEntries)

      case LauncherType.DummyNative =>
        Parameters.DummyNative()

      case LauncherType.GraalvmNativeImage =>
        assert(appArtifacts.shared.isEmpty) // just in case

        bootstrapParamsLike(desc, appArtifacts, infoEntries, mainClass, baseJarPreamble0)

      case LauncherType.Prebuilt =>
        Parameters.Prebuilt()

      case LauncherType.ScalaNative =>
        assert(appArtifacts.shared.isEmpty) // just in case

        val fetch = simpleFetch(cache, coursierRepositories)
        val nativeVersion = appArtifacts.platformSuffixOpt
          .fold("" /* FIXME throw instead? */ )(_.stripPrefix("_native"))
        // FIXME Allow options to be tweaked
        val options = ScalaNative.ScalaNativeOptions()

        Parameters.ScalaNative(fetch, mainClass, nativeVersion)
          .copy(jars = appArtifacts.fetchResult.files, options = options, verbosity = verbosity)
    }
  }

  // TODO Remove that override
  private[coursier] def createOrUpdate(
    descOpt: Option[(AppDescriptor, Array[Byte])],
    sourceReprOpt: Option[Array[Byte]],
    dest: Path,
    currentTime: Instant = Instant.now(),
    force: Boolean = false
  ): Option[Boolean] = {

    val dest0 = actualDest(dest)

    // values before the `(tmpDest, tmpAux) =>` need to be evaluated when we hold the lock of Updatable.writing
    def update: (Path, Path) => Boolean = {

      val (desc, descRepr) = descOpt.getOrElse {
        if (Files.exists(dest0))
          InfoFile.readAppDescriptor(dest0) match {
            case None    => throw new CannotReadAppDescriptionInLauncher(dest0)
            case Some(d) => d
          }
        else
          throw new LauncherNotFound(dest0)
      }

      val sourceReprOpt0 = sourceReprOpt.orElse {
        if (Files.exists(dest0))
          InfoFile.readSource(dest0).map(_._2)
        else
          None
      }

      val prebuiltOrNotFoundUrls0 = PrebuiltApp.get(
        desc,
        cache,
        archiveCache,
        verbosity,
        platform,
        platformExtensions,
        preferPrebuilt
      )

      val appArtifacts = prebuiltOrNotFoundUrls0 match {
        case Left(_)  => desc.artifacts(cache, verbosity)
        case Right(_) => AppArtifacts.empty
      }

      val lock0 = {
        val artifacts = prebuiltOrNotFoundUrls0.map(Seq(_)).getOrElse {
          appArtifacts.fetchResult.artifacts
            .filterNot(appArtifacts.shared.toSet)
            .map {
              case (a, f) =>
                PrebuiltApp.Uncompressed(a, f)
            }
        }
        ArtifactsLock.ofArtifacts(artifacts.map(app => (app.artifact, app.file)))
      }

      val sharedLockOpt =
        if (appArtifacts.shared.isEmpty || prebuiltOrNotFoundUrls0.isRight)
          None
        else
          Some(ArtifactsLock.ofArtifacts(appArtifacts.shared))

      lazy val mainClass = {

        def foundMainClassOpt0 =
          foundMainClassOpt(
            appArtifacts.shared.map(_._2),
            appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet).map(_._2),
            verbosity,
            appArtifacts.fetchResult.resolution.rootDependencies.headOption
          )

        desc.mainClass
          .orElse(foundMainClassOpt0)
          .orElse(desc.defaultMainClass)
          .getOrElse {
            throw new NoMainClassFound(actualName(dest0))
          }
      }

      (tmpDest, tmpAux) =>
        lazy val infoEntries =
          InfoFile.extraEntries(lock0, sharedLockOpt, descRepr, sourceReprOpt0, currentTime)

        lazy val upToDate = InfoFile.upToDate(
          dest0,
          lock0,
          sharedLockOpt,
          descRepr,
          sourceReprOpt0
        )

        val shouldUpdate = force || !upToDate

        if (shouldUpdate) {

          // Computed first, as where we write things below depends on whether we are about to
          // generate a native binary.
          val prebuiltOrParams = prebuiltOrNotFoundUrls0.left.map { notFoundUrls =>
            if (
              (onlyPrebuilt && desc.launcherType.isNative) || desc.launcherType == LauncherType.Prebuilt
            )
              throw new NoPrebuiltBinaryAvailable(actualName(dest0), notFoundUrls)

            params(desc, appArtifacts, infoEntries, mainClass)
          }

          // The auxiliary file is only meant to hold native binaries: it is named ".exe" on
          // Windows, and Windows only runs files named that way if they are actual native
          // executables. Some native launcher types fall back to a JVM launcher when no prebuilt
          // binary is available for the current platform (see params). Such launchers have to be
          // written to dest itself.
          val usesAuxFile = desc.launcherType.isNative && prebuiltOrParams.left.forall(_.isNative)

          val genDest =
            if (usesAuxFile) tmpAux
            else tmpDest

          val actualLauncher = prebuiltOrParams match {
            case Left(params0) =>
              writing(genDest, verbosity, Some(currentTime)) {
                Generator.generate(params0, genDest)
                genDest
              }

            case Right(a: PrebuiltApp.Uncompressed) =>
              Files.copy(a.file.toPath, genDest, StandardCopyOption.REPLACE_EXISTING)
              FileUtil.tryMakeExecutable(genDest)
              genDest

            case Right(a: PrebuiltApp.Compressed) =>
              (a.archiveType, a.pathInArchiveOpt) match {
                case (tarType: ArchiveType.Tar, None) =>
                  ArchiveUtil.withFirstFileInCompressedTarArchive(a.file, tarType) { is =>
                    writeTo(is, genDest)
                  }
                case (tarType: ArchiveType.Tar, Some(subPath)) =>
                  ArchiveUtil.withFileInCompressedTarArchive(a.file, tarType, subPath) { is =>
                    writeTo(is, genDest)
                  }
                case (ArchiveType.Gzip, None) =>
                  ArchiveUtil.withGzipContent(a.file) { is =>
                    writeTo(is, genDest)
                  }
                case (ArchiveType.Gzip, Some(_)) =>
                  sys.error("Sub-path not supported for gzip files")
                case (ArchiveType.Zip, None) =>
                  ArchiveUtil.withFirstFileInZip(a.file) { is =>
                    writeTo(is, genDest)
                  }
                case (ArchiveType.Zip, Some(subPath)) =>
                  ArchiveUtil.withFileInZip(a.file, subPath) { is =>
                    writeTo(is, genDest)
                  }
              }

              FileUtil.tryMakeExecutable(genDest)
              genDest

            case Right(a: PrebuiltApp.ExtractedArchive) =>
              a.file.toPath
          }

          val inPlaceLauncher     = usesAuxFile && actualLauncher == genDest
          val launcherIsElsewhere = actualLauncher != genDest
          if (inPlaceLauncher || launcherIsElsewhere) {
            val preamble =
              if (inPlaceLauncher)
                if (isWin)
                  basePreamble.copy(
                    kind = Preamble.Kind.Bat,
                    command = Some("%~dp0\\" + auxName("%~n0", ".exe"))
                  )
                else
                  basePreamble.copy(
                    kind = Preamble.Kind.Sh,
                    // FIXME needs directory
                    command = Some(
                      """$(cd "$(dirname "$0")"; pwd)/""" + auxName(dest0.getFileName.toString, "")
                    )
                  )
              else {
                assert(launcherIsElsewhere)
                basePreamble.copy(
                  kind = if (isWin) Preamble.Kind.Bat else Preamble.Kind.Sh,
                  command = Some(actualLauncher.toAbsolutePath.toString)
                )
              }
            writing(tmpDest, verbosity, Some(currentTime)) {
              InfoFile.writeInfoFile(tmpDest, Some(preamble), infoEntries)
              FileUtil.tryMakeExecutable(tmpDest)
            }
          }
        }

        shouldUpdate
    }

    Updatable.writing(baseDir, dest0, auxExtension, verbosity) { (tmpDest, tmpAux) =>
      update(tmpDest, tmpAux)
    }
  }

  def maybeUpdate(
    name: String,
    update: Source => Task[Option[(String, Array[Byte])]],
    currentTime: Instant = Instant.now(),
    force: Boolean = false
  ): Task[Option[Boolean]] =
    for {
      _ <- Task.delay {
        if (verbosity >= 2)
          System.err.println(s"Looking at $name")
      }

      launcher = actualDest(name)

      writtenOpt <-
        if (Files.exists(launcher))
          for {
            sourceAndBytes <- Task.fromEither(InfoFile.readSource(launcher).toRight(
              new Exception(s"Error reading source from $launcher")
            ))
            (source, sourceBytes) = sourceAndBytes

            pathDescriptorBytes <- update(source).flatMap {
              case Some(res) => Task.point(res)
              case None =>
                Task.fail(new Exception(s"${source.id} not found in ${source.channel.repr}"))
            }
            (path, descriptorBytes) = pathDescriptorBytes

            desc <- Task.fromEither(InfoFile.appDescriptor(path, descriptorBytes))

            appInfo = {
              val info = AppInfo(desc, descriptorBytes, source, sourceBytes)
              val foundName = info.appDescriptor.nameOpt
                .getOrElse(info.source.id)
              if (foundName == name)
                info
              else
                // just in case, that shouldn't happen
                info.copy(appDescriptor = info.appDescriptor.copy(nameOpt = Some(name)))
            }

            writtenOpt <- Task.delay {
              val writtenOpt0 = createOrUpdate(appInfo, currentTime, force)
              if (!writtenOpt0.exists(!_) && verbosity >= 1)
                System.err.println(s"No new update for $name" + System.lineSeparator())
              writtenOpt0
            }
          } yield writtenOpt
        else {
          System.err.println(
            s"""Cannot find installed application '$name' (installation directory is ${launcher
                .getParent()}).
               |Try running 'cs install $name'.""".stripMargin
          )
          Task.point(Some(false))
        }
    } yield writtenOpt

  def envUpdate: EnvironmentUpdate =
    EnvironmentUpdate()
      .copy(pathLikeAppends = Seq("PATH" -> baseDir.toAbsolutePath.toString))

  private def listLaunchers(): Seq[Path] =
    if (Files.isDirectory(baseDir)) {
      var s: Stream[Path] = null
      try {
        s = Files.list(baseDir)
        s.iterator()
          .asScala
          .filter(p => p.toFile.isFile && !p.getFileName.toString.startsWith("."))
          .filter(InfoFile.isInfoFile)
          .toVector
          .sortBy(actualName)
      }
      finally if (s != null)
          s.close()
    }
    else
      Nil

  def list(): Seq[String] =
    listLaunchers().map(actualName)

  /** Same as [[list]], with the version each application was installed at, when it can be inferred
    * from its launcher.
    */
  def listWithVersions(): Seq[(String, Option[String])] =
    listLaunchers().map { p =>
      val versionOpt = InfoFile.readDescriptorAndLock(p).flatMap {
        case (desc, lock) => InstallDir.versionOf(desc, lock)
      }
      (actualName(p), versionOpt)
    }
}

object InstallDir {

  private lazy val defaultDir0: Path = {

    val fromEnv = Option(System.getenv("COURSIER_BIN_DIR")).filter(_.nonEmpty)
      .orElse(Option(System.getenv("COURSIER_INSTALL_DIR")).filter(_.nonEmpty))

    def fromProps = Option(System.getProperty("coursier.install.dir"))

    def default = coursier.paths.CoursierPaths.dataLocalDirectory().toPath.resolve("bin")

    fromEnv.orElse(fromProps).map(Paths.get(_))
      .getOrElse(default)
  }

  def defaultDir: Path =
    defaultDir0

  /** Infers the version an application was installed at, from the URLs its artifacts were
    * downloaded from.
    *
    * Nothing in the launcher records that version as such, so we look for it in the lock file: for
    * prebuilt launchers, by reversing the URL pattern the descriptor builds prebuilt URLs with, and
    * for the other launcher types, by looking for the artifact of the descriptor's main dependency
    * in the lock file, and reading its version off its Maven-layout URL.
    *
    * Returns `None` rather than a wrong version if neither applies (unknown repository layout, URL
    * pattern that changed since the app was installed, …).
    */
  private[install] def versionOf(desc: AppDescriptor, lock: ArtifactsLock): Option[String] = {
    val urls = lock.entries.toVector.map(_.url).sorted
    prebuiltVersion(desc, urls).orElse(mainDependencyVersion(desc, urls))
  }

  private def prebuiltPatterns(desc: AppDescriptor): Seq[String] = {
    def patternsOf(launcher: Option[String], binaries: Map[String, String]): Seq[String] =
      launcher.toSeq ++ binaries.valuesIterator
    val fromOverrides = desc.versionOverrides.flatMap { o =>
      patternsOf(o.prebuiltLauncher, o.prebuiltBinaries.getOrElse(Map.empty))
    }
    (patternsOf(desc.prebuiltLauncher, desc.prebuiltBinaries) ++ fromOverrides).distinct
  }

  /** Strips the decorations [[coursier.install.internal.PrebuiltApp]] accepts around prebuilt URLs
    * (a leading `"gz+"`-like archive type, a trailing `"!sub/path"`), which don't end up in the
    * artifact URL.
    */
  private def prebuiltUrlPattern(pattern: String): String = {
    val noArchiveType = {
      val idx = pattern.indexOf('+')
      if (idx < 0) pattern
      else
        ArchiveType.parse(pattern.take(idx)) match {
          case Some(_) => pattern.drop(idx + 1)
          case None    => pattern
        }
    }
    val idx = noArchiveType.indexOf('!')
    if (idx < 0) noArchiveType
    else noArchiveType.take(idx)
  }

  private def prebuiltVersion(desc: AppDescriptor, urls: Seq[String]): Option[String] = {
    val it = for {
      pattern <- prebuiltPatterns(desc).iterator
      regex   <- prebuiltUrlRegex(prebuiltUrlPattern(pattern)).iterator
      url     <- urls.iterator
      m       <- regex.findFirstMatchIn(url)
    } yield m.group(1)
    if (it.hasNext) Some(it.next()) else None
  }

  private def prebuiltUrlRegex(urlPattern: String): Option[Regex] =
    if (urlPattern.contains(versionPlaceholder)) {
      // ${version} and ${platform} can't be quoted along with the rest of the pattern, so we split
      // on them, and quote what's in-between
      val regex = urlPattern
        .split(Pattern.quote(versionPlaceholder), -1)
        .map {
          _.split(Pattern.quote(platformPlaceholder), -1)
            .map(Pattern.quote)
            .mkString("[^/]*")
        }
        .mkString("([^/]+)")
      // platform extensions (".exe", ".bat") can be appended to the URL the pattern gives
      Some(("^" + regex + "(?:\\.[^./]+)?$").r)
    }
    else
      None

  private def versionPlaceholder  = "${version}"
  private def platformPlaceholder = "${platform}"

  private def mainDependencyVersion(desc: AppDescriptor, urls: Seq[String]): Option[String] = {
    val mainDeps = desc.dependencies.headOption.toSeq ++
      desc.versionOverrides.flatMap(_.dependencies.toSeq.flatMap(_.headOption))
    val modules = mainDeps.map(_.module).distinct
    val it = for {
      mod <- modules.iterator
      (org, name, exactName) = mod match {
        case j: JavaOrScalaModule.JavaModule =>
          (j.module.organization.value, j.module.name.value, true)
        case s: JavaOrScalaModule.ScalaModule =>
          // the Scala suffix of the actual module isn't known here
          (s.baseModule.organization.value, s.baseModule.name.value, false)
      }
      url     <- urls.iterator
      version <- versionFromMavenUrl(org, name, exactName, url).iterator
    } yield version
    if (it.hasNext) Some(it.next()) else None
  }

  /** Reads the version off a Maven-layout URL, like
    * `https://repo1.maven.org/maven2/org/scalameta/scalafmt-cli_2.13/3.9.6/scalafmt-cli_2.13-3.9.6.jar`
    * for organization `org.scalameta` and module name `scalafmt-cli`.
    */
  private def versionFromMavenUrl(
    org: String,
    name: String,
    exactName: Boolean,
    url: String
  ): Option[String] = {
    val orgParts = org.split('.').toVector
    val parts    = url.split('/').toVector
    def nameMatches(s: String) =
      s == name || (!exactName && s.startsWith(name + "_"))
    val it = parts.indices.iterator.filter { idx =>
      idx >= orgParts.length &&
      idx + 2 < parts.length &&
      nameMatches(parts(idx)) &&
      parts.slice(idx - orgParts.length, idx) == orgParts &&
      parts(idx + 2).startsWith(parts(idx) + "-" + parts(idx + 1))
    }
    if (it.hasNext) Some(parts(it.next() + 1)) else None
  }

  private def classpathEntry(a: Artifact, f: File, forceResource: Boolean = false): ClassPathEntry =
    if (forceResource || a.changing || a.url.startsWith("file:"))
      ClassPathEntry.Resource(
        f.getName,
        f.lastModified(),
        Files.readAllBytes(f.toPath)
      )
    else
      ClassPathEntry.Url(a.url)

  private def foundMainClassOpt(
    shared: Seq[File],
    jars: Seq[File],
    verbosity: Int,
    mainDependencyOpt: Option[Dependency]
  ): Option[String] = {
    val m = MainClass.mainClasses(jars)
    if (verbosity >= 2) {
      System.err.println(s"Found ${m.size} main classes:")
      for (a <- m)
        System.err.println(s"  $a")
    }
    MainClass.retainedMainClassOpt(
      m,
      mainDependencyOpt.map(d => (d.module.organization.value, d.module.name.value))
    ) // appArtifacts.fetchResult.resolution.rootDependencies.headOption)
  }

  private def simpleFetch(
    cache: Cache[Task],
    repositories: Seq[Repository]
  ): Seq[String] => Seq[File] = {

    val fetch = coursier.Fetch(cache)
      .withRepositories(repositories)

    deps =>
      import coursier.core.{ModuleName, Organization}
      import coursier.util.StringInterpolators._
      import coursier.util.Task

      val deps0 = deps.map { dep =>
        dep.split(":", 3) match {
          case Array(org, name, ver) =>
            Dependency(
              Module(Organization(org), ModuleName(name), Map.empty),
              VersionConstraint(ver)
            )
          case _ => ???
        }
      }

      fetch.addDependencies(deps0: _*).run()
  }

  private def writing[T](
    path: Path,
    verbosity: Int,
    modifiedTime: Option[Instant] = None
  )(f: => T): T = {

    if (verbosity >= 2)
      System.err.println(s"Writing $path")
    val t = f
    for (time <- modifiedTime)
      Files.setLastModifiedTime(path, FileTime.from(time))
    if (verbosity >= 1)
      System.err.println(s"Wrote $path")
    t
  }

  def auxName(name: String, auxExtension: String): String = {
    val (name0, _) = {
      val idx = name.lastIndexOf('.')
      if (idx >= 0)
        (name.take(idx), name.drop(idx))
      else
        (name, "")
    }

    s".$name0.aux$auxExtension"
  }

  def platformExtensions(os: String): Seq[String] = {

    val os0 = os.toLowerCase(Locale.ROOT)

    if (os0.contains("windows"))
      Seq(".exe", ".bat")
    else
      Nil
  }

  def platformExtensions(): Seq[String] =
    Option(System.getProperty("os.name"))
      .toSeq
      .flatMap(platformExtensions(_))

  private def writeTo(is: InputStream, dest: Path): Unit = {
    var os: OutputStream = null
    try {
      os =
        Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      val buf  = Array.ofDim[Byte](16384)
      var read = -1
      while ({ read = is.read(buf); read >= 0 })
        if (read > 0)
          os.write(buf, 0, read)
    }
    finally if (os != null)
        os.close()
  }

}
