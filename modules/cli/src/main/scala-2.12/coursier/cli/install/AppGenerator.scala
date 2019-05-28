package coursier.cli.install

import java.io.{File, FileInputStream}
import java.math.BigInteger
import java.net.URLClassLoader
import java.nio.channels.{FileChannel, FileLock}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.{ZipEntry, ZipFile}

import coursier.bootstrap.{Assembly, ClassLoaderContent, ClasspathEntry, LauncherBat}
import coursier.Fetch
import coursier.cache.{Cache, CacheLocks}
import coursier.cache.internal.FileUtil
import coursier.cli.app.{AppArtifacts, AppDescriptor, LauncherType, RawAppDescriptor, RawSource, Source}
import coursier.cli.launch.Launch
import coursier.core.{Artifact, Dependency}
import coursier.util.Task

object AppGenerator {

  def sha1(f: File): String = {
    val md = MessageDigest.getInstance("SHA-1")

    var is: FileInputStream = null
    try {
      is = new FileInputStream(f)
      FileUtil.withContent(is, md.update(_, 0, _))
    } finally is.close()

    val b = md.digest()
    new BigInteger(1, b).toString(16)
  }

  def lock(res: Fetch.Result): Lock =
    lock(res.artifacts)

  def lock(artifacts: Seq[(Artifact, File)]): Lock = {

    val entries = artifacts.map {
      case (a, f) =>
        Lock.Entry(a.url, "SHA-1", sha1(f))
    }

    Lock(entries.toSet)
  }

  def classpathEntry(a: Artifact, f: File, forceResource: Boolean = false): ClasspathEntry =
    if (forceResource || a.changing || a.url.startsWith("file:"))
      ClasspathEntry.Resource(
        f.getName,
        f.lastModified(),
        Files.readAllBytes(f.toPath)
      )
    else
      ClasspathEntry.Url(a.url)

  def lockFilePath = "META-INF/coursier/lock-file"
  def sharedLockFilePath = "META-INF/coursier/shared-deps-lock-file"
  def jsonDescFilePath = "META-INF/coursier/info.json"
  def jsonSourceFilePath = "META-INF/coursier/info-source.json"

  def readAppDescriptor(f: Path): Option[(AppDescriptor, Array[Byte])] = {

    var zf: ZipFile = null

    try {
      zf = new ZipFile(f.toFile)
      val entOpt = Option(zf.getEntry(jsonDescFilePath))

      entOpt.map { ent =>
        val content = FileUtil.readFully(zf.getInputStream(ent))
        val e = RawAppDescriptor.parse(new String(content, StandardCharsets.UTF_8))
          .left.map(err => new ErrorParsingAppDescription(s"$f!$jsonDescFilePath", err))
          .right.flatMap { r =>
            r.appDescriptor
              .toEither
              .left.map { errors =>
                new ErrorProcessingAppDescription(s"$f!$jsonDescFilePath", errors.toList.mkString(", "))
              }
          }
        val desc = e.fold(throw _, identity)
        (desc, content)
      }
    } finally {
      if (zf != null)
        zf.close()
    }
  }

  def readSource(f: Path): Option[(Source, Array[Byte])] = {

    var zf: ZipFile = null

    try {
      zf = new ZipFile(f.toFile)
      val entOpt = Option(zf.getEntry(jsonSourceFilePath))

      entOpt.map { ent =>
        val content = FileUtil.readFully(zf.getInputStream(ent))
        val e = RawSource.parse(new String(content, StandardCharsets.UTF_8))
          .left.map(err => new ErrorParsingSource(s"$f!$jsonSourceFilePath", err))
          .right.flatMap { r =>
            r.source
              .toEither
              .left.map { errors =>
                new ErrorProcessingSource(s"$f!$jsonSourceFilePath", errors.toList.mkString(", "))
              }
          }
        val source = e.fold(throw _, identity)
        (source, content)
      }
    } finally {
      if (zf != null)
        zf.close()
    }
  }

  private def writing[T](baseDir: Path, dest: Path, verbosity: Int)(f: Path => T): T = {

    // ensuring we're the only process trying to write dest
    // - acquiring a lock (lockFile) while writing
    // - writing things to a temp file (tmpDest)
    // - atomic move to final dest, so that no borked launcher are exposed at any time, even during launcher generation

    val dir = dest.getParent
    val tmpDest = dir.resolve(s".${dest.getFileName}.part")
    val lockFile = dir.resolve(s".${dest.getFileName}.lock")
    var channel: FileChannel = null
    try {
      channel = CacheLocks.withStructureLock(baseDir) {
        Files.createDirectories(dir)
        FileChannel.open(
          lockFile,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.DELETE_ON_CLOSE
        )
      }
      var lock: FileLock = null
      try {
        lock = channel.lock()
        val res = f(tmpDest)
        if (Files.isRegularFile(tmpDest)) {
          if (verbosity >= 2) {
            System.err.println(s"Wrote $tmpDest")
            System.err.println(s"Moving $tmpDest to $dest")
          }
          Files.deleteIfExists(dest) // StandardCopyOption.REPLACE_EXISTING doesn't seem to work along with ATOMIC_MOVE
          Files.move(
            tmpDest,
            dest,
            StandardCopyOption.ATOMIC_MOVE
          )
          if (verbosity == 1)
            System.err.println(s"Wrote $dest")
        }
        res
      } finally {
        if (lock != null)
          lock.release()
      }
    } finally {
      if (channel != null)
        channel.close()
      Files.deleteIfExists(lockFile)
    }
  }

  private def foundMainClassOpt(
    shared: Seq[File],
    jars: Seq[File],
    verbosity: Int,
    mainDependencyOpt: Option[Dependency]
  ): Option[String] = {

    val baseParent = Launch.baseLoader

    val parent =
      if (shared.isEmpty) baseParent
      else {
        val urls = shared.map(_.toURI.toURL)
        if (verbosity >= 2) {
          System.err.println(s"${urls.length} JARs in shared class loader:")
          for (url <- urls)
            System.err.println(s"  $url")
        }
        new URLClassLoader(urls.toArray, baseParent)
      }

    val loader = {
      val urls = jars.map(_.toURI.toURL) // appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet).map(_._2.toURI.toURL)
      if (verbosity >= 2) {
        System.err.println(s"${urls.length} JARs in class loader:")
        for (url <- urls)
          System.err.println(s"  $url")
      }
      new URLClassLoader(urls.toArray, parent)
    }

    val m = Launch.mainClasses(loader)
    if (verbosity >= 2) {
      System.err.println(s"Found ${m.size} main classes:")
      for (a <- m)
        System.err.println(s"  $a")
    }
    Launch.retainedMainClassOpt(m, mainDependencyOpt) // appArtifacts.fetchResult.resolution.rootDependencies.headOption)
  }

  def createOrUpdate(
    descOpt: Option[(AppDescriptor, Array[Byte])],
    sourceReprOpt: Option[Array[Byte]],
    cache: Cache[Task],
    baseDir: Path,
    dest: Path,
    currentTime: Instant = Instant.now(),
    verbosity: Int = 0,
    force: Boolean = false,
    graalvmParamsOpt: Option[GraalvmParams] = None
  ): Boolean =
    writing(baseDir, dest, verbosity) { tmpDest =>

      val (desc, descRepr) = descOpt.getOrElse {
        if (Files.exists(dest))
          readAppDescriptor(dest) match {
            case None => throw new CannotReadAppDescriptionInLauncher(dest)
            case Some(d) => d
          }
        else
          throw new LauncherNotFound(dest)
      }

      val sourceReprOpt0 = sourceReprOpt.orElse {
        if (Files.exists(dest))
          readSource(dest).map(_._2)
        else
          None
      }

      val appArtifacts = AppArtifacts(desc, cache, verbosity)

      val sharedLockOpt =
        if (appArtifacts.shared.isEmpty)
          None
        else
          Some(lock(appArtifacts.shared))

      val lock0 = {
        val artifacts = appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet)
        lock(artifacts)
      }

      lazy val upToDate = Files.exists(dest) && {

        // TODO Look for files on the side too (native launchers)

        var f: ZipFile = null
        try {
          f = new ZipFile(dest.toFile)
          val lockEntryOpt = Option(f.getEntry(lockFilePath))
          val sharedLockEntryOpt = Option(f.getEntry(sharedLockFilePath))
          val descFileEntryOpt = Option(f.getEntry(jsonDescFilePath))
          val sourceFileEntryOpt = Option(f.getEntry(jsonSourceFilePath))

          def read(ent: ZipEntry): Array[Byte] =
            FileUtil.readFully(f.getInputStream(ent))

          def readLock(ent: ZipEntry): Lock = {
            // FIXME Don't just throw in case of malformed file?
            val s = new String(read(ent), StandardCharsets.UTF_8)
            Lock.read(s) match {
              case Left(err) => throw new ErrorReadingLockFile(s"$dest!${ent.getName}", err)
              case Right(l) => l
            }
          }

          val initialAppDesc = descFileEntryOpt.map(read(_).toSeq)
          val initialSource = sourceFileEntryOpt.map(read(_).toSeq)

          val initialLockOpt: Option[Lock] = lockEntryOpt.map(readLock)
          val initialSharedLockOpt: Option[Lock] = sharedLockEntryOpt.map(readLock)

          initialLockOpt.contains(lock0) &&
            initialSharedLockOpt == sharedLockOpt &&
            initialAppDesc.contains(descRepr.toSeq) &&
            initialSource == sourceReprOpt0.map(_.toSeq)
        } finally {
          if (f != null)
            f.close()
        }
      }

      val mainClass = {

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

      if (!force && upToDate)
        false
      else {
        val lockFileEntry = {
          val e = new ZipEntry(lockFilePath)
          e.setLastModifiedTime(FileTime.from(currentTime))
          e.setCompressedSize(-1L)
          val content = lock0.repr.getBytes(StandardCharsets.UTF_8)
          e -> content
        }

        val sharedLockFileEntryOpt = sharedLockOpt.map { lock =>
          val e = new ZipEntry(sharedLockFilePath)
          e.setLastModifiedTime(FileTime.from(currentTime))
          e.setCompressedSize(-1L)
          val content = lock.repr.getBytes(StandardCharsets.UTF_8)
          e -> content
        }

        val destEntry = {
          val e = new ZipEntry(jsonDescFilePath)
          e.setLastModifiedTime(FileTime.from(currentTime))
          e.setCompressedSize(-1L)
          e -> descRepr
        }

        val sourceEntryOpt = sourceReprOpt0.map { sourceRepr =>
          val e = new ZipEntry(jsonSourceFilePath)
          e.setLastModifiedTime(FileTime.from(currentTime))
          e.setCompressedSize(-1L)
          e -> sourceRepr
        }

        val extraEntries = Seq(destEntry, lockFileEntry) ++ sourceEntryOpt.toSeq ++ sharedLockFileEntryOpt.toSeq

        desc.launcherType match {
          case LauncherType.Bootstrap | LauncherType.Standalone =>
            val isStandalone = desc.launcherType == LauncherType.Standalone
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
            coursier.bootstrap.Bootstrap.create(
              sharedContentOpt.toSeq :+ mainContent,
              mainClass,
              tmpDest,
              desc.javaOptions,
              javaProperties = desc.javaProperties ++ appArtifacts.extraProperties,
              deterministic = true,
              extraZipEntries = extraEntries
            )

          case LauncherType.Assembly | LauncherType.GraalvmNativeImage =>

            assert(appArtifacts.shared.isEmpty) // just in case

            val isNativeImage = desc.launcherType == LauncherType.GraalvmNativeImage

            val (assemblyDest, hookOpt, graalvmParamsOpt0) =
              if (isNativeImage) {
                val graalvmParams = graalvmParamsOpt.getOrElse {
                  throw new NoGraalvmInstallationPassed
                }
                val tmpFile = Files.createTempFile(s"coursier-install-${dest.getFileName}-assembly", ".jar")
                val hook: Thread =
                  new Thread("cleanup") {
                    override def run() =
                      Files.deleteIfExists(tmpFile)
                  }
                Runtime.getRuntime.addShutdownHook(hook)
                (tmpFile, Some(hook), Some(graalvmParams))
              } else
                (tmpDest, None, None)

            try {
              // FIXME Allow to adjust merge rules?
              Assembly.create(
                appArtifacts.fetchResult.artifacts.map(_._2),
                desc.javaOptions,
                mainClass,
                assemblyDest,
                extraZipEntries = extraEntries
              )

              for (graalvmParams <- graalvmParamsOpt0) {
                val cmd = Seq(s"${graalvmParams.home}/bin/native-image", "--no-server") ++
                  graalvmParams.extraNativeImageOptions ++
                  Seq("-jar", assemblyDest.toString, tmpDest.toString)
                val b = new ProcessBuilder(cmd: _*)
                  .inheritIO()
                val p = b.start()
                val retCode = p.waitFor()
                if (retCode != 0)
                  throw new ErrorRunningGraalvmNativeImage(retCode)

                // TODO Write app descriptor and lock file in hidden files on the side
              }
            } finally {
              for (hook <- hookOpt) {
                hook.run()
                Runtime.getRuntime.removeShutdownHook(hook)
              }
            }
        }

        if (desc.launcherType.needsBatOnWindows && LauncherBat.isWindows) {
          val bat = dest.getParent.resolve(dest.getFileName.toString + ".bat")
          if (verbosity >= 2)
            System.err.println(s"Writing $bat")
          LauncherBat.create(
            bat,
            desc.javaOptions
          )
          Files.setLastModifiedTime(bat, FileTime.from(currentTime))
          if (verbosity >= 1)
            System.err.println(s"Wrote $bat")
        }

        Files.setLastModifiedTime(tmpDest, FileTime.from(currentTime))
        true
      }
    }

  sealed abstract class AppGeneratorException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  final class NoMainClassFound extends AppGeneratorException("No main class found")

  // FIXME Keep more details
  final class NoScalaVersionFound extends AppGeneratorException("No scala version found")

  final class LauncherNotFound(path: Path) extends AppGeneratorException(s"$path not found")

  final class CannotReadAppDescriptionInLauncher(path: Path)
    extends AppGeneratorException(s"Cannot read app description in $path")

  final class CannotReadSourceInLauncher(path: Path)
    extends AppGeneratorException(s"Cannot read source info in $path")

  final class ErrorReadingLockFile(path: String, details: String)
    extends AppGeneratorException(s"Error reading lock file $path: $details")

  final class ErrorParsingSource(path: String, details: String)
    extends AppGeneratorException(s"Error parsing source $path: $details")
  final class ErrorProcessingSource(path: String, details: String)
    extends AppGeneratorException(s"Error processing source $path: $details")

  final class ErrorParsingAppDescription(path: String, details: String)
    extends AppGeneratorException(s"Error parsing app description $path: $details")
  final class ErrorProcessingAppDescription(path: String, details: String)
    extends AppGeneratorException(s"Error processing app description $path: $details")

  final class NoGraalvmInstallationPassed extends AppGeneratorException("No graalvm installation found")

  final class ErrorRunningGraalvmNativeImage(retCode: Int)
    extends AppGeneratorException(s"native-image returned non-zero exit code $retCode")

}
