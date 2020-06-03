package coursier.jvm

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import java.util.Locale

import coursier.cache.{Cache, CacheLocks, CacheLogger, FileCache}
import coursier.cache.internal.ThreadUtil
import coursier.paths.CoursierPaths
import coursier.util.{Artifact, Task}
import coursier.version.Version
import dataclass.data

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.control.NonFatal

@data class JvmCache(
  baseDirectory: File = JvmCache.defaultBaseDirectory,
  cache: Cache[Task] = FileCache(),
  os: String = JvmIndex.defaultOs(),
  architecture: String = JvmIndex.defaultArchitecture(),
  defaultJdkNameOpt: Option[String] = Some(JvmCache.defaultJdkName),
  defaultVersionOpt: Option[String] = Some(JvmCache.defaultVersion),
  defaultLogger: Option[JvmCacheLogger] = None,
  index: Option[Task[JvmIndex]] = None,
  maxWaitDuration: Option[Duration] = Some(1.minute),
  durationBetweenChecks: FiniteDuration = 2.seconds,
  scheduledExecutor: Option[ScheduledExecutorService] = Some(JvmCache.defaultScheduledExecutor),
  currentTime: () => Instant = () => Instant.now(),
  unArchiver: UnArchiver = UnArchiver.default(),
  handleLoggerLifecycle: Boolean = true
) {

  def withDefaultLogger(logger: JvmCacheLogger): JvmCache =
    withDefaultLogger(Some(logger))


  private def tryExtract(
    entry: JvmIndexEntry,
    dir: File,
    logger0: JvmCacheLogger,
    archive: File,
    tmpDir: File
  ) =
    withLockFor(dir) {
      logger0.extracting(entry.id, archive.getAbsolutePath, dir)
      val dir0 = try {

        JvmCache.deleteRecursive(tmpDir)
        unArchiver.extract(entry.archiveType, archive, tmpDir, overwrite = false)

        val rootDir = tmpDir.listFiles().filter(!_.getName.startsWith(".")) match {
          case Array() =>
            throw new JvmCache.EmptyArchive(archive, entry.url)
          case Array(rootDir0) =>
            if (rootDir0.isDirectory)
              rootDir0
            else
              throw new JvmCache.NoDirectoryFoundInArchive(archive, entry.url)
          case other =>
            throw new JvmCache.UnexpectedContentInArchive(archive, entry.url, other.map(_.getName).toSeq)
        }
        Files.move(rootDir.toPath, dir.toPath, StandardCopyOption.ATOMIC_MOVE)
        JvmCache.deleteRecursive(tmpDir)
        dir
      } catch {
        case NonFatal(e) =>
          logger0.extractionFailed(entry.id, entry.url, dir, e)
          throw e
      }
      logger0.extracted(entry.id, entry.url, dir)
      dir0
    }

  private def tryRemove(
    id: String,
    dir: File,
    logger0: JvmCacheLogger
  ): Option[Boolean] =
    withLockFor(dir) {
      // TODO logger0.removing(id, dir)
      dir.exists() && {
        try JvmCache.deleteRecursive(dir)
        catch {
          case NonFatal(e) =>
            // TODO logger0.removingFailed(id, dir, e)
            throw e
        }
        // TODO logger0.removed(id, dir)
        true
      }
    }

  def getIfInstalled(id: String): Task[Option[(String, File)]] = {
    entry(id).flatMap {
      case Left(err) => Task.fail(new JvmCache.JvmNotFoundInIndex(id, err))
      case Right(entry0) =>
        val dir = baseDirectoryOf(entry0.id)
        Task.delay(dir.isDirectory).map {
          case true => Some((entry0.id, JvmCache.finalDirectory(dir, os)))
          case false => None
        }
    }
  }

  def get(
    entry: JvmIndexEntry,
    logger: Option[JvmCacheLogger],
    installIfNeeded: Boolean
  ): Task[File] = {
    val dir = baseDirectoryOf(entry.id)
    val tmpDir = tempDirectory(dir)
    val logger0 = logger.orElse(defaultLogger).getOrElse(JvmCacheLogger.nop)

    Task.delay(currentTime()).flatMap { initialAttempt =>

      lazy val task: Task[File] =
        Task.delay(dir.isDirectory).flatMap {
          case true => Task.point(dir)
          case false =>
            if (installIfNeeded) {
              val maybeArchiveTask = cache.loggerOpt.filter(_ => handleLoggerLifecycle).getOrElse(CacheLogger.nop).using {
                cache.file(Artifact(entry.url).withChanging(entry.version.endsWith("SNAPSHOT"))).run
              }
              maybeArchiveTask.flatMap {
                case Left(err) => Task.fail(err)
                case Right(archive) =>
                  val tryExtract0 = Task.delay {
                    tryExtract(
                      entry,
                      dir,
                      logger0,
                      archive,
                      tmpDir
                    )
                  }

                  tryExtract0.flatMap {
                    case Some(dir) => Task.point(dir)
                    case None =>
                      (maxWaitDuration, scheduledExecutor) match {
                        case (Some(max), Some(executor)) =>

                          val shouldTryAgainTask = Task.delay {
                            val now = currentTime()
                            val elapsedMs = now.toEpochMilli - initialAttempt.toEpochMilli
                            elapsedMs < max.toMillis
                          }

                          shouldTryAgainTask.flatMap {
                            case false =>
                              Task.fail(new JvmCache.TimeoutOnLockedDirectory(dir, max))
                            case true =>
                              Task.completeAfter(executor, durationBetweenChecks)
                                .flatMap { _ => task }
                          }

                        case _ =>
                          Task.fail(new JvmCache.LockedDirectory(dir))
                      }
                  }
              }
            } else
              Task.fail(new JvmCache.JvmNotFound(entry.id, dir))
        }

        task.map(JvmCache.finalDirectory(_, os))
    }
  }

  def entry(id: String): Task[Either[String, JvmIndexEntry]] =
    JvmCache.idToNameVersion(id, defaultJdkNameOpt, defaultVersionOpt) match {
      case None =>
        Task.fail(new JvmCache.MalformedJvmId(id))
      case Some((name, ver)) =>
        index match {
          case None => Task.fail(new JvmCache.NoIndexSpecified)
          case Some(indexTask) =>
            indexTask.flatMap { index0 =>
              Task.point(index0.lookup(name, ver, Some(os), Some(architecture)))
            }
        }
    }

  def delete(id: String): Task[Option[Boolean]] =
    delete(id, None)

  def delete(
    id: String,
    logger: Option[JvmCacheLogger]
  ): Task[Option[Boolean]] = {
    val dir = baseDirectoryOf(id)
    val logger0 = logger.orElse(defaultLogger).getOrElse(JvmCacheLogger.nop)

    Task.delay(dir.isDirectory).flatMap {
      case true =>
        Task.delay(tryRemove(id, dir, logger0))
      case false =>
        Task.point(Some(false))
    }
  }

  def get(
    entry: JvmIndexEntry,
    logger: Option[JvmCacheLogger]
  ): Task[File] =
    get(entry, logger, installIfNeeded = true)

  def get(
    entry: JvmIndexEntry
  ): Task[File] =
    get(entry, None, installIfNeeded = true)

  def get(id: String): Task[File] =
    get(id, installIfNeeded = true)
  def get(id: String, installIfNeeded: Boolean): Task[File] =
    entry(id).flatMap {
      case Left(err) => Task.fail(new JvmCache.JvmNotFoundInIndex(id, err))
      case Right(entry0) => get(entry0, None, installIfNeeded)
    }

  def idOf(javaHome: File): Option[String] = {

    val javaHomeRoot =
      if (os == "darwin")
        Option(javaHome.getParentFile)
          .flatMap(f => Option(f.getParentFile))
          .getOrElse(javaHome)
      else
        javaHome

    val isManaged = Option(javaHomeRoot.getParentFile)
      .exists(_.toPath.normalize.toAbsolutePath == baseDirectory.toPath.normalize.toAbsolutePath)

    if (isManaged)
      Some(javaHomeRoot.getName)
    else
      None
  }


  private def withLockFor[T](dir: File)(f: => T): Option[T] =
    CacheLocks.withLockOr(baseDirectory, dir)(Some(f), Some(None))

  private def assertValidEntry(entry: JvmIndexEntry): Unit = {
    assert(entry.os == os)
    assert(entry.architecture == architecture)
  }

  private def tempDirectory(dir: File): File =
    new File(dir.getParentFile, "." + dir.getName + ".part")

  def directory(entry: JvmIndexEntry): File = {
    assertValidEntry(entry)
    directory(entry.id)
  }

  private def baseDirectoryOf(id: String): File = {
    assert(!id.contains("/"))
    new File(baseDirectory, id)
  }

  def directory(id: String): File =
    JvmCache.finalDirectory(baseDirectoryOf(id), os)

  def installed(): Task[Seq[String]] =
    Task.delay {
      Option(baseDirectory.listFiles())
        .map(_.toVector)
        .getOrElse(Vector.empty)
        .filter { f =>
          !f.getName.startsWith(".") &&
            f.isDirectory
            // TODO Check that a java executable is there too?
        }
        .map(_.getName)
        .map { id =>
          val idx = id.indexOf('@')
          if (idx < 0)
            (id, "", Version(""))
          else
            (id.take(idx), "@", Version(id.drop(idx + 1)))
        }
        .sorted
        .map {
          case (name, sep, ver) =>
            name + sep + ver.repr
        }
    }

  def withIndex(index: Task[JvmIndex]): JvmCache =
    withIndex(Some(index))

  def withDefaultIndex: JvmCache = {
    val indexTask = cache.loggerOpt.filter(_ => handleLoggerLifecycle).getOrElse(CacheLogger.nop).using {
      JvmIndex.load(cache)
    }
    withIndex(indexTask)
  }

}

object JvmCache {

  def defaultBaseDirectory: File =
    defaultBaseDirectory0

  def defaultJdkName: String =
    "adopt"
  def defaultVersion: String =
    "[1,)"

  def idToNameVersion(id: String): Option[(String, String)] =
    idToNameVersion(id, Some(defaultJdkName), Some(defaultVersion))

  def idToNameVersion(id: String, defaultJdkNameOpt: Option[String]): Option[(String, String)] =
    idToNameVersion(id, defaultJdkNameOpt, Some(defaultVersion))

  def idToNameVersion(id: String, defaultJdkNameOpt: Option[String], defaultVersionOpt: Option[String]): Option[(String, String)] = {

    def splitAt(separator: Char): Option[(String, String)] = {
      val idx = id.indexOf(separator)
      if (idx < 0)
        None
      else
        Some((id.take(idx), id.drop(idx + 1)))
    }

    val viaAt = splitAt('@')
    def viaColon = splitAt(':')

    def defaultJdk =
      defaultJdkNameOpt.flatMap { defaultName =>
        if (id.headOption.exists(_.isDigit))
          Some((defaultName, "1." + id.stripPrefix("1.").stripSuffix("+") + "+")) // FIXME "1." stuff is specific to some JDKs
        else
          None
      }

    def defaultVersion =
      defaultVersionOpt.flatMap { defaultVer =>
        if (id.headOption.exists(_.isLetter))
          Some((id, defaultVer))
        else
          None
      }

    viaAt
      .orElse(viaColon)
      .orElse(defaultJdk)
      .orElse(defaultVersion)
  }


  private def finalDirectory(dir: File, os: String): File =
    if (os == "darwin")
      new File(dir, "Contents/Home")
    else
      dir

  private lazy val defaultScheduledExecutor: ScheduledExecutorService = {
    val e = new ScheduledThreadPoolExecutor(1, ThreadUtil.daemonThreadFactory())
    e.setKeepAliveTime(1, TimeUnit.MINUTES)
    e.allowCoreThreadTimeOut(true)
    e
  }


  private lazy val defaultBaseDirectory0: File =
    CoursierPaths.jvmCacheDirectory()

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  sealed abstract class JvmCacheException(message: String, parent: Throwable = null)
    extends Exception(message, parent)

  final class EmptyArchive(val archive: File, val archiveUrl: String)
    extends JvmCacheException(s"$archive is empty (from $archiveUrl)")
  final class NoDirectoryFoundInArchive(val archive: File, val archiveUrl: String)
    extends JvmCacheException(s"$archive does not contain a directory (from $archiveUrl)")

  final class UnexpectedContentInArchive(val archive: File, val archiveUrl: String, val rootFileNames: Seq[String])
    extends JvmCacheException(s"Unexpected content at the root of $archive (from $archiveUrl): ${rootFileNames.mkString(", ")}")

  final class TimeoutOnLockedDirectory(val directory: File, val timeout: Duration)
    extends JvmCacheException(s"Directory $directory is locked")

  final class LockedDirectory(val directory: File)
    extends JvmCacheException(s"Directory $directory is locked")

  final class JvmNotFound(val id: String, val expectedLocation: File)
    extends JvmCacheException(s"JVM $id not found at $expectedLocation")

  final class NoIndexSpecified
    extends JvmCacheException("No index specified")

  final class MalformedJvmId(val id: String)
    extends JvmCacheException(s"Malformed JVM id '$id'")

  final class JvmNotFoundInIndex(val id: String, val reason: String)
    extends JvmCacheException(s"JVM $id not found in index: $reason")

}
