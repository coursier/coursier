package coursier.install

import java.io.{File, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.time.Instant
import java.util.Locale
import java.util.stream.Stream
import java.util.zip.ZipEntry

import coursier.Fetch
import coursier.cache.{ArchiveCache, ArchiveType, Cache, FileCache}
import coursier.core.{Dependency, Repository}
import coursier.env.EnvironmentUpdate
import coursier.install.error._
import coursier.install.internal._
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

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.Properties

@data class InstallDir(
  baseDir: Path = InstallDir.defaultDir,
  cache: Cache[Task] = FileCache(),
  @since
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
  @since
  overrideProguardedBootstraps: Option[Boolean] = None,
  @since("2.0.17")
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
      .withJavaOpts(desc.javaOptions)
      .withJvmOptionFile(desc.jvmOptionFile)

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
          .withPythonJep(desc.jna.contains("python-jep"))
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
            .orElse(graalvmParamsOpt.map(_.defaultVersion))
          home <- nativeImageJavaHome.map(_(ver).unsafeRun()(cache.ec)) // meh
        } yield home

        Parameters.NativeImage(mainClass, fetch)
          .withGraalvmOptions(
            desc.graalvmOptions.toSeq.flatMap(_.options) ++
              graalvmParamsOpt.map(_.extraNativeImageOptions).getOrElse(Nil)
          )
          .withGraalvmVersion(graalvmParamsOpt.map(_.defaultVersion))
          .withJars(appArtifacts.fetchResult.files)
          .withNameOpt(Some(desc.nameOpt.getOrElse(dest.getFileName.toString)))
          .withJavaHome(graalvmHomeOpt)
          .withVerbosity(verbosity)

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

          val actualLauncher = prebuiltOrNotFoundUrls0 match {
            case Left(notFoundUrls) =>
              if (
                (onlyPrebuilt && desc.launcherType.isNative) || desc.launcherType == LauncherType.Prebuilt
              )
                throw new NoPrebuiltBinaryAvailable(notFoundUrls)

              val params0 = params(desc, appArtifacts, infoEntries, mainClass, dest)

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

          val inPlaceLauncher     = desc.launcherType.isNative && actualLauncher == genDest
          val launcherIsElsewhere = actualLauncher != genDest
          if (inPlaceLauncher || launcherIsElsewhere) {
            val preamble =
              if (inPlaceLauncher)
                if (isWin)
                  basePreamble
                    .withKind(Preamble.Kind.Bat)
                    .withCommand("%~dp0\\" + auxName("%~n0", ".exe"))
                else
                  basePreamble
                    .withKind(Preamble.Kind.Sh)
                    .withCommand(
                      // FIXME needs directory
                      """$(cd "$(dirname "$0")"; pwd)/""" + auxName(dest0.getFileName.toString, "")
                    )
              else {
                assert(launcherIsElsewhere)
                basePreamble
                  .withKind(if (isWin) Preamble.Kind.Bat else Preamble.Kind.Sh)
                  .withCommand(actualLauncher.toAbsolutePath.toString)
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
      finally if (s != null)
          s.close()
    }
    else
      Nil
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
