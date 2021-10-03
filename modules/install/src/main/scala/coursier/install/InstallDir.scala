package coursier.install

import java.io.{BufferedInputStream, File, FileInputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.time.Instant
import java.util.Locale
import java.util.stream.Stream
import java.util.zip.{GZIPInputStream, ZipEntry, ZipFile}

import coursier.Fetch
import coursier.cache.{ArchiveType, ArtifactError, Cache, FileCache}
import coursier.cache.loggers.ProgressBarRefreshDisplay
import coursier.core.{Dependency, Repository}
import coursier.env.EnvironmentUpdate
import coursier.launcher.{
  AssemblyGenerator,
  BootstrapGenerator,
  ClassLoaderContent,
  ClassPathEntry,
  Generator,
  NativeImageGenerator,
  Parameters,
  Preamble,
  ScalaNativeGenerator
}
import coursier.launcher.internal.FileUtil
import coursier.launcher.native.NativeBuilder
import coursier.launcher.Parameters.ScalaNative
import coursier.util.{Artifact, Task}
import dataclass._
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.CompressorStreamFactory

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

@data class InstallDir(
  baseDir: Path = InstallDir.defaultDir,
  cache: Cache[Task] = FileCache(),
  @since
  verbosity: Int = 0,
  graalvmParamsOpt: Option[GraalvmParams] = None,
  coursierRepositories: Seq[Repository] = Nil,
  platform: Option[String] = InstallDir.platform(),
  platformExtensions: Seq[String] = InstallDir.platformExtensions(),
  os: String = System.getProperty("os.name", ""),
  nativeImageJavaHome: Option[String => Task[File]] = None,
  onlyPrebuilt: Boolean = false,
  preferPrebuilt: Boolean = true,
  basePreamble: Preamble = Preamble()
    .addExtraEnvVar(InstallDir.isInstalledLauncherEnvVar, "true"),
  @since
  overrideProguardedBootstraps: Option[Boolean] = None
) {

  private lazy val isWindows =
    os.toLowerCase(Locale.ROOT).contains("windows")

  private lazy val auxExtension =
    if (isWindows) ".exe"
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
    if (isWindows) name.stripSuffix(".bat")
    else name
  }

  private def actualDest(dest: Path): Path =
    if (isWindows) dest.getParent.resolve(dest.getFileName.toString + ".bat")
    else dest

  private def baseJarPreamble(desc: AppDescriptor): Preamble =
    basePreamble.addExtraEnvVar(InstallDir.isJvmLauncherEnvVar, "true")
      .withOsKind(isWindows)
      .callsItself(isWindows)
      .withJavaOpts(desc.javaOptions)
      .withJvmOptionFile(desc.jvmOptionFile)
  private def baseNativePreamble: Preamble =
    basePreamble.addExtraEnvVar(InstallDir.isNativeLauncherEnvVar, "true")

  private[install] def params(
    desc: AppDescriptor,
    appArtifacts: AppArtifacts,
    infoEntries: Seq[(ZipEntry, Array[Byte])],
    mainClass: String,
    dest: Path
  ): Parameters = {

    val baseJarPreamble0 = baseJarPreamble(desc)

    desc.launcherType match {
      case LauncherType.DummyJar =>
        Parameters.Bootstrap(Nil, mainClass)
          .withPreamble(baseJarPreamble0)
          .withJavaProperties(desc.javaProperties ++ appArtifacts.extraProperties)
          .withDeterministic(true)
          .withHybridAssembly(desc.launcherType == LauncherType.Hybrid)
          .withExtraZipEntries(infoEntries)

      case _: LauncherType.BootstrapLike =>
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
          .withPreamble(baseJarPreamble0)
          .withJavaProperties(desc.javaProperties ++ appArtifacts.extraProperties)
          .withDeterministic(true)
          .withHybridAssembly(desc.launcherType == LauncherType.Hybrid)
          .withExtraZipEntries(infoEntries)
          .withPython(desc.jna.contains("python"))

        overrideProguardedBootstraps
          .fold(params0)(params0.withProguarded)

      case LauncherType.Assembly =>
        assert(appArtifacts.shared.isEmpty) // just in case

        // FIXME Allow to adjust merge rules?
        Parameters.Assembly()
          .withPreamble(baseJarPreamble0)
          .withFiles(appArtifacts.fetchResult.files)
          .withMainClass(mainClass)
          .withExtraZipEntries(infoEntries)

      case LauncherType.DummyNative =>
        Parameters.DummyNative()

      case LauncherType.GraalvmNativeImage =>
        assert(appArtifacts.shared.isEmpty) // just in case

        val fetch = simpleFetch(cache, coursierRepositories)

        val graalvmHomeOpt = for {
          ver <- desc.graalvmOptions.flatMap(_.version)
            .orElse(graalvmParamsOpt.flatMap(_.defaultVersion))
          home <- nativeImageJavaHome.map(_(ver).unsafeRun()(cache.ec)) // meh
        } yield home

        Parameters.NativeImage(mainClass, fetch)
          .withGraalvmOptions(
            desc.graalvmOptions.toSeq.flatMap(_.options) ++
              graalvmParamsOpt.map(_.extraNativeImageOptions).getOrElse(Nil)
          )
          .withGraalvmVersion(graalvmParamsOpt.flatMap(_.defaultVersion))
          .withJars(appArtifacts.fetchResult.files)
          .withNameOpt(Some(desc.nameOpt.getOrElse(dest.getFileName.toString)))
          .withJavaHome(graalvmHomeOpt)
          .withVerbosity(verbosity)

      case LauncherType.ScalaNative =>
        assert(appArtifacts.shared.isEmpty) // just in case

        val fetch = simpleFetch(cache, coursierRepositories)
        val nativeVersion = appArtifacts.platformSuffixOpt
          .fold("" /* FIXME throw instead? */ )(_.stripPrefix("_native"))
        // FIXME Allow options to be tweaked
        val options = ScalaNative.ScalaNativeOptions()

        Parameters.ScalaNative(fetch, mainClass, nativeVersion)
          .withJars(appArtifacts.fetchResult.files)
          .withOptions(options)
          .withVerbosity(verbosity)
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

      val prebuiltOrNotFoundUrls0 = prebuiltOrNotFoundUrls(
        desc,
        cache,
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
                (a, f, None)
            }
        }
        ArtifactsLock.ofArtifacts(artifacts.map { case (a, f, _) => (a, f) })
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
            throw new NoMainClassFound
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

          val genDest =
            if (desc.launcherType.isNative) tmpAux
            else tmpDest

          prebuiltOrNotFoundUrls0 match {
            case Left(notFoundUrls) =>
              if (onlyPrebuilt && desc.launcherType.isNative)
                throw new NoPrebuiltBinaryAvailable(notFoundUrls)

              val params0 = params(desc, appArtifacts, infoEntries, mainClass, dest)

              writing(genDest, verbosity, Some(currentTime)) {
                Generator.generate(params0, genDest)
              }

            case Right((_, prebuilt, archiveTypeAndPathOpt)) =>
              // decompress if needed

              archiveTypeAndPathOpt match {
                case Some((ArchiveType.Tgz, None)) =>
                  withFirstFileInTgz(prebuilt) { is =>
                    writeTo(is, genDest)
                  }
                case Some((ArchiveType.Tgz, Some(subPath))) =>
                  withFileInTgz(prebuilt, subPath) { is =>
                    writeTo(is, genDest)
                  }
                case Some((ArchiveType.Gzip, None)) =>
                  withGzipContent(prebuilt) { is =>
                    writeTo(is, genDest)
                  }
                case Some((ArchiveType.Gzip, Some(_))) =>
                  sys.error("Sub-path not supported for gzip files")
                case Some((ArchiveType.Zip, None)) =>
                  withFirstFileInZip(prebuilt) { is =>
                    writeTo(is, genDest)
                  }
                case Some((ArchiveType.Zip, Some(subPath))) =>
                  withFileInZip(prebuilt, subPath) { is =>
                    writeTo(is, genDest)
                  }
                case None =>
                  Files.copy(prebuilt.toPath, genDest, StandardCopyOption.REPLACE_EXISTING)
              }

              FileUtil.tryMakeExecutable(genDest)
          }

          if (desc.launcherType.isNative) {
            val preamble =
              if (isWindows)
                baseNativePreamble
                  .withKind(Preamble.Kind.Bat)
                  .withCommand("%~dp0\\" + auxName("%~n0", ".exe"))
              else
                baseNativePreamble
                  .withKind(Preamble.Kind.Sh)
                  .withCommand(
                    """"$(cd "$(dirname "$0")"; pwd)/""" +
                      auxName(dest0.getFileName.toString, "") +
                      "\""
                  ) // FIXME needs directory
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

      sourceAndBytes <- Task.fromEither(
        InfoFile.readSource(launcher).toRight(new Exception(s"Error reading source from $launcher"))
      )
      (source, sourceBytes) = sourceAndBytes

      pathDescriptorBytes <- update(source).flatMap {
        case Some(res) => Task.point(res)
        case None => Task.fail(new Exception(s"${source.id} not found in ${source.channel.repr}"))
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
          info.withAppDescriptor(info.appDescriptor.withNameOpt(Some(name)))
      }

      writtenOpt <- Task.delay {
        val writtenOpt0 = createOrUpdate(appInfo, currentTime, force)
        if (!writtenOpt0.exists(!_) && verbosity >= 1)
          System.err.println(s"No new update for $name" + System.lineSeparator())
        writtenOpt0
      }
    } yield writtenOpt

  def envUpdate: EnvironmentUpdate =
    EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> baseDir.toAbsolutePath.toString))

  def list(): Seq[String] =
    if (Files.isDirectory(baseDir)) {
      var s: Stream[Path] = null
      try {
        s = Files.list(baseDir)
        s.iterator()
          .asScala
          .filter(p => !p.getFileName.toString.startsWith("."))
          .filter(InfoFile.isInfoFile)
          .map(actualName)
          .toVector
          .sorted
      }
      finally {
        if (s != null)
          s.close()
      }
    }
    else
      Nil
}

object InstallDir {

  val isInstalledLauncherEnvVar: String = "IS_CS_INSTALLED_LAUNCHER"
  val isJvmLauncherEnvVar: String       = "CS_JVM_LAUNCHER"
  val isNativeLauncherEnvVar: String    = "CS_NATIVE_LAUNCHER"

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
      import coursier.core.ModuleName
      import coursier.{Dependency, Module, organizationString}
      import coursier.util.Task
      import coursier.core.Organization

      val deps0 = deps.map { dep =>
        dep.split(":", 3) match {
          case Array(org, name, ver) =>
            Dependency(Module(Organization(org), ModuleName(name)), ver)
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

  private def candidatePrebuiltArtifacts(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int,
    platform: Option[String],
    platformExtensions: Seq[String],
    preferPrebuilt: Boolean
  ): Option[Seq[(Artifact, Option[(ArchiveType, Option[String])])]] = {

    def mainVersionsIterator(): Iterator[String] = {
      val it0 = desc.candidateMainVersions(cache, verbosity)
      val it =
        if (it0.hasNext) it0
        else desc.mainVersionOpt.iterator
      // check the latest 5 versions if preferPrebuilt is true
      // FIXME Don't hardcode that number?
      it.take(if (preferPrebuilt) 5 else 1)
    }

    def urlArchiveType(url: String): (String, Option[(ArchiveType, Option[String])]) = {
      val idx = url.indexOf('+')
      if (idx < 0) (url, None)
      else
        ArchiveType.parse(url.take(idx)) match {
          case Some(tpe) =>
            val url0         = url.drop(idx + 1)
            val subPathIndex = url0.indexOf('!')
            if (subPathIndex < 0)
              (url0, Some((tpe, None)))
            else {
              val subPath = url0.drop(subPathIndex + 1)
              (url0.take(subPathIndex), Some((tpe, Some(subPath))))
            }
          case None =>
            (url, None)
        }
    }

    def patternArtifacts(
      pattern: String
    ): Seq[(Artifact, Option[(ArchiveType, Option[String])])] = {

      val artifactsIt = for {
        version <- mainVersionsIterator()
        isSnapshot = version.endsWith("SNAPSHOT")
        baseUrl0 = pattern
          .replace("${version}", version)
          .replace("${platform}", platform.getOrElse(""))
        (baseUrl, archiveTypeAndPathOpt) = urlArchiveType(baseUrl0)
        ext <-
          if (archiveTypeAndPathOpt.forall(_._2.nonEmpty))
            platformExtensions.iterator ++ Iterator("")
          else Iterator("")
        (url, archiveTypeAndPathOpt0) = archiveTypeAndPathOpt match {
          case None                  => (baseUrl + ext, archiveTypeAndPathOpt)
          case Some((tpe, subPath0)) => (baseUrl, Some((tpe, subPath0.map(_ + ext))))
        }
      } yield (Artifact(url).withChanging(isSnapshot), archiveTypeAndPathOpt0)

      artifactsIt.toVector
    }

    if (desc.launcherType.isNative)
      desc.prebuiltLauncher
        .orElse(desc.prebuiltBinaries.get(platform.getOrElse("")))
        .map(patternArtifacts)
    else
      None
  }

  private[coursier] def handleArtifactErrors(
    maybeFile: Either[ArtifactError, File],
    artifact: Artifact,
    verbosity: Int
  ): Option[File] =
    maybeFile match {
      case Left(e: ArtifactError.NotFound) =>
        if (verbosity >= 2)
          System.err.println(s"No prebuilt launcher found at ${artifact.url}")
        None
      case Left(e: ArtifactError.DownloadError)
          if e.getCause.isInstanceOf[javax.net.ssl.SSLHandshakeException] =>
        // These seem to happen on Windows for non existing artifacts, only from the native launcher apparently???
        // Interpreting these errors as not-found-errors too.
        if (verbosity >= 2)
          System.err.println(
            s"No prebuilt launcher found at ${artifact.url} (SSL handshake exception)"
          )
        None
      case Left(e) =>
        // FIXME Ignore some other kind of errors too? Just warn about them?
        throw new DownloadError(artifact.url, e)
      case Right(f) =>
        if (verbosity >= 1) {
          val size = ProgressBarRefreshDisplay.byteCount(Files.size(f.toPath))
          System.err.println(s"Found prebuilt launcher at ${artifact.url} ($size)")
        }
        Some(f)
    }

  private def prebuiltOrNotFoundUrls(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int,
    platform: Option[String],
    platformExtensions: Seq[String],
    preferPrebuilt: Boolean
  ): Either[Seq[String], (Artifact, File, Option[(ArchiveType, Option[String])])] = {

    def downloadArtifacts(
      artifacts: Seq[(Artifact, Option[(ArchiveType, Option[String])])]
    ): Iterator[(Artifact, File, Option[(ArchiveType, Option[String])])] =
      artifacts.iterator.flatMap {
        case (artifact, archiveTypeOpt) =>
          if (verbosity >= 2)
            System.err.println(s"Checking prebuilt launcher at ${artifact.url}")
          cache.loggerOpt.foreach(_.init())
          val maybeFile =
            try cache.file(artifact).run.unsafeRun()(cache.ec)
            finally cache.loggerOpt.foreach(_.stop())
          handleArtifactErrors(maybeFile, artifact, verbosity)
            .iterator
            .map((artifact, _, archiveTypeOpt))
      }

    candidatePrebuiltArtifacts(
      desc,
      cache,
      verbosity,
      platform,
      platformExtensions,
      preferPrebuilt
    ).toRight(Nil).flatMap { artifacts =>
      val iterator = downloadArtifacts(artifacts)
      if (iterator.hasNext) Right(iterator.next())
      else Left(artifacts.map(_._1.url))
    }
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
      Seq(".exe")
    else
      Nil
  }

  def platformExtensions(): Seq[String] =
    Option(System.getProperty("os.name"))
      .toSeq
      .flatMap(platformExtensions(_))

  @deprecated("Use the override accepting two arguments instead", "2.0.10")
  def platform(os: String): Option[String] = {
    platform(os, Option(System.getProperty("os.arch")).getOrElse("x86_64"))
  }

  def platform(os: String, arch: String): Option[String] = {

    val os0   = os.toLowerCase(Locale.ROOT)
    val arch0 = if (arch == "amd64") "x86_64" else arch

    if (os0.contains("linux"))
      Some(s"$arch0-pc-linux")
    else if (os0.contains("mac"))
      Some(s"$arch0-apple-darwin")
    else if (os0.contains("windows"))
      Some(s"$arch0-pc-win32")
    else
      None
  }

  def platform(): Option[String] =
    for {
      os   <- Option(System.getProperty("os.name"))
      arch <- Option(System.getProperty("os.arch"))
      p    <- platform(os, arch)
    } yield p

  private def withTgzEntriesIterator[T](
    tgz: File
  )(
    f: Iterator[(ArchiveEntry, InputStream)] => T
  ): T = {
    // https://alexwlchan.net/2019/09/unpacking-compressed-archives-in-scala/
    var fis: FileInputStream = null
    try {
      fis = new FileInputStream(tgz)
      val uncompressedInputStream = new CompressorStreamFactory().createCompressorInputStream(
        if (fis.markSupported()) fis
        else new BufferedInputStream(fis)
      )
      val archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
        if (uncompressedInputStream.markSupported()) uncompressedInputStream
        else new BufferedInputStream(uncompressedInputStream)
      )

      var nextEntryOrNull: ArchiveEntry = null

      val it: Iterator[(ArchiveEntry, InputStream)] =
        new Iterator[(ArchiveEntry, InputStream)] {
          def hasNext: Boolean = {
            if (nextEntryOrNull == null)
              nextEntryOrNull = archiveInputStream.getNextEntry
            nextEntryOrNull != null
          }
          def next(): (ArchiveEntry, InputStream) = {
            assert(hasNext)
            val value = (nextEntryOrNull, archiveInputStream)
            nextEntryOrNull = null
            value
          }
        }

      f(it)
    }
    finally {
      if (fis != null)
        fis.close()
    }
  }

  private def withFirstFileInTgz[T](tgz: File)(f: InputStream => T): T =
    withTgzEntriesIterator(tgz) { it =>
      val it0 = it.filter(!_._1.isDirectory).map(_._2)
      if (it0.hasNext)
        f(it0.next())
      else
        throw new NoSuchElementException(s"No file found in $tgz")
    }

  private def withFileInTgz[T](tgz: File, pathInArchive: String)(f: InputStream => T): T =
    withTgzEntriesIterator(tgz) { it =>
      val it0 = it.collect {
        case (ent, is) if !ent.isDirectory && ent.getName == pathInArchive =>
          is
      }
      if (it0.hasNext)
        f(it0.next())
      else
        throw new NoSuchElementException(s"$pathInArchive not found in $tgz")
    }

  private def withGzipContent[T](gzFile: File)(f: InputStream => T): T = {
    var fis: FileInputStream  = null
    var gzis: GZIPInputStream = null
    try {
      fis = new FileInputStream(gzFile)
      gzis = new GZIPInputStream(fis)
      f(gzis)
    }
    finally {
      if (gzis != null) gzis.close()
      if (fis != null) fis.close()
    }
  }

  private def withFirstFileInZip[T](zip: File)(f: InputStream => T): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = zf.entries().asScala.find(e => !e.isDirectory).getOrElse {
        throw new NoSuchElementException(s"No file found in $zip")
      }
      is = zf.getInputStream(ent)
      f(is)
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

  private def withFileInZip[T](zip: File, pathInArchive: String)(f: InputStream => T): T = {
    var zf: ZipFile     = null
    var is: InputStream = null
    try {
      zf = new ZipFile(zip)
      val ent = Option(zf.getEntry(pathInArchive)).getOrElse {
        throw new NoSuchElementException(s"$pathInArchive not found in $zip")
      }
      is = zf.getInputStream(ent)
      f(is)
    }
    finally {
      if (zf != null)
        zf.close()
      if (is != null)
        is.close()
    }
  }

  private def writeTo(is: InputStream, dest: Path): Unit = {
    var os: OutputStream = null
    try {
      os =
        Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      val buf  = Array.ofDim[Byte](16384)
      var read = -1
      while ({ read = is.read(buf); read >= 0 }) {
        if (read > 0)
          os.write(buf, 0, read)
      }
    }
    finally {
      if (os != null)
        os.close()
    }
  }

  sealed abstract class InstallDirException(message: String, cause: Throwable = null)
      extends Exception(message, cause)

  final class NoMainClassFound extends InstallDirException("No main class found")

  // FIXME Keep more details
  final class NoScalaVersionFound extends InstallDirException("No scala version found")

  final class LauncherNotFound(val path: Path) extends InstallDirException(s"$path not found")

  final class NoPrebuiltBinaryAvailable(val candidateUrls: Seq[String])
      extends InstallDirException(
        if (candidateUrls.isEmpty)
          "No prebuilt binary available"
        else
          s"No prebuilt binary available at ${candidateUrls.mkString(", ")}"
      )

  final class CannotReadAppDescriptionInLauncher(val path: Path)
      extends InstallDirException(s"Cannot read app description in $path")

  final class NotAnApplication(val path: Path)
      extends InstallDirException(s"File $path wasn't installed by cs install")

  final class DownloadError(val url: String, cause: Throwable = null)
      extends InstallDirException(s"Error downloading $url", cause)

}
