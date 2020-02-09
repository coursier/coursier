package coursier.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.Instant
import java.util.Locale

import coursier.Fetch
import coursier.cache.{ArtifactError, Cache, FileCache}
import coursier.cache.loggers.ProgressBarRefreshDisplay
import coursier.core.{Dependency, Repository}
import coursier.launcher.{AssemblyGenerator, BootstrapGenerator, ClassLoaderContent, ClassPathEntry, Generator, NativeImageGenerator, Parameters, Preamble, ScalaNativeGenerator}
import coursier.launcher.internal.FileUtil
import coursier.launcher.native.NativeBuilder
import coursier.launcher.Parameters.ScalaNative
import coursier.util.{Artifact, Task}
import dataclass._

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
  os: String = System.getProperty("os.name", "")
) {

  private lazy val isWindows =
    os.toLowerCase(Locale.ROOT).contains("windows")

  import InstallDir._

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo,
  ): Boolean =
    createOrUpdate(appInfo, Instant.now(), force = false)

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo,
    currentTime: Instant
  ): Boolean =
    createOrUpdate(appInfo, currentTime, force = false)

  // TODO Make that return a Task[Boolean] instead
  def createOrUpdate(
    appInfo: AppInfo,
    currentTime: Instant,
    force: Boolean
  ): Boolean = {

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

  // TODO Remove that override
  private[coursier] def createOrUpdate(
    descOpt: Option[(AppDescriptor, Array[Byte])],
    sourceReprOpt: Option[Array[Byte]],
    dest: Path,
    currentTime: Instant = Instant.now(),
    force: Boolean = false
  ): Boolean = {

    val dest0 =
      if (isWindows) dest.getParent.resolve(dest.getFileName.toString + ".bat")
      else dest
    val auxExtension =
      if (isWindows) ".exe"
      else ""

    // values before the `(tmpDest, tmpAux) =>` need to be evaluated when we hold the lock of Updatable.writing
    def update: (Path, Path) => Boolean = {

      val (desc, descRepr) = descOpt.getOrElse {
        if (Files.exists(dest0))
          InfoFile.readAppDescriptor(dest0) match {
            case None => throw new CannotReadAppDescriptionInLauncher(dest0)
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

      val prebuiltOpt0 = prebuiltOpt(desc, cache, verbosity)

      val appArtifacts = prebuiltOpt0 match {
        case None => desc.artifacts(cache, verbosity)
        case Some(_) => AppArtifacts.empty
      }

      val lock0 = {
        val artifacts = prebuiltOpt0.map(Seq(_)).getOrElse {
          appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet)
        }
        ArtifactsLock.ofArtifacts(artifacts)
      }

      val sharedLockOpt =
        if (appArtifacts.shared.isEmpty || prebuiltOpt0.nonEmpty)
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

        lazy val infoEntries = InfoFile.extraEntries(lock0, sharedLockOpt, descRepr, sourceReprOpt0, currentTime)

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

          prebuiltOpt0 match {
            case None =>
              val params = desc.launcherType match {
                case LauncherType.DummyJar =>
                  Parameters.Bootstrap(Nil, mainClass)
                    .withPreamble(
                      Preamble()
                        .withOsKind(isWindows)
                        .callsItself(isWindows)
                        .withJavaOpts(desc.javaOptions)
                    )
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

                  Parameters.Bootstrap(sharedContentOpt.toSeq :+ mainContent, mainClass)
                    .withPreamble(
                      Preamble()
                        .withOsKind(isWindows)
                        .callsItself(isWindows)
                        .withJavaOpts(desc.javaOptions)
                    )
                    .withJavaProperties(desc.javaProperties ++ appArtifacts.extraProperties)
                    .withDeterministic(true)
                    .withHybridAssembly(desc.launcherType == LauncherType.Hybrid)
                    .withExtraZipEntries(infoEntries)

                case LauncherType.Assembly =>

                  assert(appArtifacts.shared.isEmpty) // just in case

                  // FIXME Allow to adjust merge rules?
                  Parameters.Assembly()
                    .withPreamble(
                      Preamble()
                        .withOsKind(isWindows)
                        .callsItself(isWindows)
                        .withJavaOpts(desc.javaOptions)
                    )
                    .withFiles(appArtifacts.fetchResult.files.map(_.toFile)) // could fail on virtual FS (keeping files for ZipFile)
                    .withMainClass(mainClass)
                    .withExtraZipEntries(infoEntries)

                case LauncherType.DummyNative =>
                  Parameters.DummyNative()

                case LauncherType.GraalvmNativeImage =>

                  assert(appArtifacts.shared.isEmpty) // just in case

                  val fetch = simpleFetch(cache, coursierRepositories)

                  Parameters.NativeImage(mainClass, fetch)
                    .withGraalvmOptions(desc.graalvmOptions.toSeq.flatMap(_.options) ++ graalvmParamsOpt.map(_.extraNativeImageOptions).getOrElse(Nil))
                    .withJars(appArtifacts.fetchResult.files.map(_.toFile))
                    .withNameOpt(Some(desc.nameOpt.getOrElse(dest0.getFileName.toString)))
                    .withVerbosity(verbosity)

                case LauncherType.ScalaNative =>

                  assert(appArtifacts.shared.isEmpty) // just in case

                  val fetch = simpleFetch(cache, coursierRepositories)
                  val nativeVersion = appArtifacts.platformSuffixOpt
                    .fold("" /* FIXME throw instead? */)(_.stripPrefix("_native"))
                  // FIXME Allow options to be tweaked
                  val options = ScalaNative.ScalaNativeOptions()

                  Parameters.ScalaNative(fetch, mainClass, nativeVersion)
                    .withJars(appArtifacts.fetchResult.files.map(_.toFile))
                    .withOptions(options)
                    .withVerbosity(verbosity)
              }

              writing(genDest, verbosity, Some(currentTime)) {
                Generator.generate(params, genDest)
              }

            case Some((_, prebuilt)) =>
              Files.copy(prebuilt, genDest, StandardCopyOption.REPLACE_EXISTING)
              FileUtil.tryMakeExecutable(prebuilt)
          }

          if (desc.launcherType.isNative) {
            val preamble =
              if (isWindows)
                Preamble()
                  .withKind(Preamble.Kind.Bat)
                  .withCommand("%~dp0\\" + auxName("%~n0", ".exe"))
              else
                Preamble()
                  .withKind(Preamble.Kind.Sh)
                  .withCommand(""""$(cd "$(dirname "$0")"; pwd)/""" + auxName(dest0.getFileName.toString, "") + "\"") // FIXME needs directory
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
    }.getOrElse {
      sys.error(s"Could not acquire lock for $dest0")
    }
  }

  def maybeUpdate(
    name: String,
    update: Source => Task[Option[(String, Array[Byte])]],
    currentTime: Instant = Instant.now(),
    force: Boolean = false
  ): Task[Boolean] =
    for {
      _ <- Task.delay {
        if (verbosity >= 2)
          System.err.println(s"Looking at $name")
      }

      launcher = baseDir.resolve(name)

      sourceAndBytes <- Task.fromEither(InfoFile.readSource(launcher).toRight(new Exception(s"Error reading source from $launcher")))
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

      written <- Task.delay {
        val written0 = InstallDir(baseDir, cache)
          .withVerbosity(verbosity)
          .withGraalvmParamsOpt(graalvmParamsOpt)
          .withCoursierRepositories(coursierRepositories)
          .createOrUpdate(
            appInfo,
            currentTime,
            force
          )
        if (!written0 && verbosity >= 1)
          System.err.println(s"No new update for $name\n")
        written0
      }
    } yield written

}

object InstallDir {

  private lazy val defaultDir0: Path =
    // TODO Take some env var / Java props into account too
    coursier.paths.CoursierPaths.dataLocalDirectory().toPath.resolve("bin")

  def defaultDir: Path =
    defaultDir0

  private def classpathEntry(a: Artifact, f: Path, forceResource: Boolean = false): ClassPathEntry =
    if (forceResource || a.changing || a.url.startsWith("file:"))
      ClassPathEntry.Resource(
        f.getFileName.toString,
        Files.getLastModifiedTime(f).toInstant.toEpochMilli,
        Files.readAllBytes(f)
      )
    else
      ClassPathEntry.Url(a.url)

  private def foundMainClassOpt(
    shared: Seq[Path],
    jars: Seq[Path],
    verbosity: Int,
    mainDependencyOpt: Option[Dependency]
  ): Option[String] = {
    val m = MainClass.mainClasses(jars)
    if (verbosity >= 2) {
      System.err.println(s"Found ${m.size} main classes:")
      for (a <- m)
        System.err.println(s"  $a")
    }
    MainClass.retainedMainClassOpt(m, mainDependencyOpt.map(d => (d.module.organization.value, d.module.name.value))) // appArtifacts.fetchResult.resolution.rootDependencies.headOption)
  }

  private def simpleFetch(cache: Cache[Task], repositories: Seq[Repository]): Seq[String] => Seq[File] = {

    val fetch  = coursier.Fetch(cache)
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

      fetch.addDependencies(deps0: _*).run().map(_.toFile)
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

  private def prebuiltOpt(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int
  ): Option[(Artifact, Path)] =
    desc.prebuiltLauncher.filter(_ => desc.launcherType.isNative).flatMap { pattern =>
      val version = desc.retainedMainVersion(cache, verbosity)
        .orElse(desc.mainVersionOpt)
        .getOrElse("")
      val baseUrl = pattern
        .replaceAllLiterally("${version}", version)
        .replaceAllLiterally("${platform}", platform.getOrElse(""))
      val urls = platformExtensions.map(ext => baseUrl + ext) ++ Seq(baseUrl)

      val iterator = urls.iterator.flatMap { url =>
        if (verbosity >= 2)
          System.err.println(s"Checking prebuilt launcher at $url")
        // FIXME Make this changing all the time? Allow to change that?
        val artifact = Artifact(url).withChanging(version.endsWith("SNAPSHOT"))
        cache.loggerOpt.foreach(_.init())
        val maybeFile =
          try cache.file(artifact).run.unsafeRun()(cache.ec)
          finally cache.loggerOpt.foreach(_.stop())
        maybeFile match {
          case Left(e: ArtifactError.NotFound) =>
            if (verbosity >= 2)
              System.err.println(s"No prebuilt launcher found at $url")
            Iterator.empty
          case Left(e) => throw e // FIXME Ignore some other kind of errors too? Just warn about them?
          case Right(f) =>
            if (verbosity >= 1) {
              val size = ProgressBarRefreshDisplay.byteCount(Files.size(f))
              System.err.println(s"Found prebuilt launcher at $url ($size)")
            }
            Iterator.single((artifact, f))
        }
      }

      if (iterator.hasNext)
        Some(iterator.next())
      else
        None
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

  def platform(os: String): Option[String] = {

    val os0 = os.toLowerCase(Locale.ROOT)

    if (os0.contains("linux"))
      Some("x86_64-pc-linux")
    else if (os0.contains("mac"))
      Some("x86_64-apple-darwin")
    else if (os0.contains("windows"))
      Some("x86_64-pc-win32")
    else
      None
  }

  def platform(): Option[String] =
    Option(System.getProperty("os.name"))
      .flatMap(platform(_))

  sealed abstract class AppGeneratorException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  final class NoMainClassFound extends AppGeneratorException("No main class found")

  // FIXME Keep more details
  final class NoScalaVersionFound extends AppGeneratorException("No scala version found")

  final class LauncherNotFound(path: Path) extends AppGeneratorException(s"$path not found")

  final class CannotReadAppDescriptionInLauncher(path: Path)
    extends AppGeneratorException(s"Cannot read app description in $path")

}
