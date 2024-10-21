package coursier.jvm

import java.io.File
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService

import coursier.cache.{ArchiveCache, Cache, CacheDefaults, CacheLogger, FileCache, UnArchiver}
import coursier.core.Repository
import coursier.util.{Artifact, Task}
import dataclass.data

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

// format: off
@data class JvmCache(
  os: String = JvmIndex.defaultOs(),
  architecture: String = JvmIndex.defaultArchitecture(),
  defaultJdkNameOpt: Option[String] = Some(JvmCache.defaultJdkName),
  defaultVersionOpt: Option[String] = Some(JvmCache.defaultVersion),

  index: Option[Task[JvmIndex]] = None,

  handleLoggerLifecycle: Boolean = true,

  @since("2.0.17")
  archiveCache: ArchiveCache[Task] = ArchiveCache()
) {
  // format: on

  def getIfInstalled(id: String): Task[Option[File]] =
    if (id.contains("://"))
      getIfInstalled(Artifact.fromUrl(id))
    else
      entries(id).flatMap {
        case Left(err) => Task.fail(new JvmCache.JvmNotFoundInIndex(id, err))
        case Right(entries0) =>
          entries0
            .reverse
            .map(getIfInstalled(_))
            .foldLeft(Task.point(Option.empty[File])) {
              (acc, task) =>
                acc.flatMap {
                  case None    => task
                  case Some(f) => Task.point(Some(f))
                }
            }
      }

  def getIfInstalled(entry: JvmIndexEntry): Task[Option[File]] = {
    val artifact = Artifact(entry.url).withChanging(entry.version.endsWith("SNAPSHOT"))
    getIfInstalled(artifact)
  }

  def getIfInstalled(artifact: Artifact): Task[Option[File]] =
    archiveCache.getIfExists(artifact).flatMap {
      case Left(e)          => Task.fail(e)
      case Right(None)      => Task.point(None)
      case Right(Some(dir)) => JvmCache.finalDirectory(artifact.url, dir, os).map(Some(_))
    }

  def get(
    entry: JvmIndexEntry,
    logger: Option[JvmCacheLogger]
  ): Task[File] = {
    val artifact = Artifact(entry.url).withChanging(entry.version.endsWith("SNAPSHOT"))
    get(artifact, logger)
  }

  def get(
    artifact: Artifact,
    logger: Option[JvmCacheLogger]
  ): Task[File] = {

    val task = archiveCache.get(artifact).flatMap {
      case Left(err) => Task.fail(err)
      case Right(f)  => Task.point(f)
    }

    task.flatMap(JvmCache.finalDirectory(artifact.url, _, os))
  }

  def entries(id: String): Task[Either[String, Seq[JvmIndexEntry]]] =
    JvmCache.idToNameVersion(id, defaultJdkNameOpt, defaultVersionOpt) match {
      case None =>
        Task.fail(new JvmCache.MalformedJvmId(id))
      case Some((name, ver)) =>
        index match {
          case None => Task.fail(new JvmCache.NoIndexSpecified)
          case Some(indexTask) =>
            indexTask.map { index0 =>
              index0.lookup(name, ver, Some(os), Some(architecture))
            }
        }
    }

  // seems installIfNeeded is unused in the 'get' methods below :|

  def get(
    entry: JvmIndexEntry
  ): Task[File] =
    get(entry, None)

  def get(id: String): Task[File] =
    if (id.contains("://"))
      get(Artifact.fromUrl(id), None)
    else
      entries(id).flatMap {
        case Left(err)       => Task.fail(new JvmCache.JvmNotFoundInIndex(id, err))
        case Right(entries0) => get(entries0.last, None)
      }

  def withIndex(index: Task[JvmIndex]): JvmCache =
    withIndex(Some(index))

  def withIndex(indexUrl: String): JvmCache = {
    val indexTask = archiveCache.cache
      .loggerOpt
      .filter(_ => handleLoggerLifecycle)
      .getOrElse(CacheLogger.nop)
      .using {
        JvmIndex.load(archiveCache.cache, indexUrl)
      }
    withIndex(indexTask)
  }

  def withIndexChannel(
    repositories: Seq[Repository],
    indexChannel: JvmChannel,
    os: Option[String],
    architecture: Option[String]
  ): JvmCache = {
    val indexTask = archiveCache.cache
      .loggerOpt
      .filter(_ => handleLoggerLifecycle)
      .getOrElse(CacheLogger.nop)
      .using {
        JvmIndex.load(archiveCache.cache, repositories, indexChannel, os, architecture)
      }
    withIndex(indexTask)
  }

  @deprecated("Use the override accepting os and architecture", "2.1.15")
  def withIndexChannel(repositories: Seq[Repository], indexChannel: JvmChannel): JvmCache =
    withIndexChannel(repositories, indexChannel, None, None)

  def withDefaultIndex: JvmCache = {
    val indexTask = archiveCache.cache
      .loggerOpt
      .filter(_ => handleLoggerLifecycle)
      .getOrElse(CacheLogger.nop)
      .using {
        JvmIndex.load(archiveCache.cache)
      }
    withIndex(indexTask)
  }

}

object JvmCache {

  def defaultJdkName: String =
    "adoptium"
  def defaultVersion: String =
    "[1,)"

  def idToNameVersion(id: String): Option[(String, String)] =
    idToNameVersion(id, Some(defaultJdkName), Some(defaultVersion))

  def idToNameVersion(id: String, defaultJdkNameOpt: Option[String]): Option[(String, String)] =
    idToNameVersion(id, defaultJdkNameOpt, Some(defaultVersion))

  def idToNameVersion(
    id: String,
    defaultJdkNameOpt: Option[String],
    defaultVersionOpt: Option[String]
  ): Option[(String, String)] = {

    def splitAt(separator: Char): Option[(String, String)] = {
      val idx = id.indexOf(separator)
      if (idx < 0)
        None
      else
        Some((id.take(idx), id.drop(idx + 1)))
    }

    val viaAt    = splitAt('@')
    def viaColon = splitAt(':')

    def defaultJdk =
      defaultJdkNameOpt.flatMap { defaultName =>
        if (id.headOption.exists(_.isDigit))
          Some((defaultName, id.stripSuffix("+") + "+"))
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

  private def finalDirectory(url: String, dir: File, os: String): Task[File] = {

    val rootDirTask = Task.delay(dir.listFiles().filter(!_.getName.startsWith("."))).flatMap {
      case Array() =>
        Task.fail(new JvmCache.EmptyArchive(dir, url))
      case Array(rootDir0) =>
        Task.delay(rootDir0.isDirectory).flatMap {
          case true =>
            Task.point(rootDir0)
          case false =>
            Task.fail(new JvmCache.NoDirectoryFoundInArchive(dir, url))
        }
      case other =>
        Task.fail {
          new JvmCache.UnexpectedContentInArchive(
            dir,
            url,
            other.map(_.getName).toSeq
          )
        }
    }

    if (os == "darwin")
      rootDirTask.flatMap { rootDir =>
        val contentsHome = new File(rootDir, "Contents/Home")
        Task.delay {
          if (contentsHome.isDirectory) contentsHome
          else rootDir
        }
      }
    else
      rootDirTask
  }

  sealed abstract class JvmCacheException(message: String, parent: Throwable = null)
      extends Exception(message, parent)

  final class EmptyArchive(val archive: File, val archiveUrl: String)
      extends JvmCacheException(s"$archive is empty (from $archiveUrl)")
  final class NoDirectoryFoundInArchive(val archive: File, val archiveUrl: String)
      extends JvmCacheException(s"$archive does not contain a directory (from $archiveUrl)")

  final class UnexpectedContentInArchive(
    val archive: File,
    val archiveUrl: String,
    val rootFileNames: Seq[String]
  ) extends JvmCacheException(
        s"Unexpected content at the root of $archive (from $archiveUrl): ${rootFileNames.mkString(", ")}"
      )

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
