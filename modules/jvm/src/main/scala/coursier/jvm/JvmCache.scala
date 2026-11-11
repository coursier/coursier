package coursier.jvm

import dataclass.{data, since}

import java.io.File
import java.util.Locale

import coursier.cache.{ArchiveCache, ArtifactError, CacheLogger, ExpectedChecksum}
import coursier.core.Repository
import coursier.util.{Artifact, Task}
import scala.concurrent.duration.Duration

// format: off
@data(deprecatedSetters = true, deprecatedSettersMessage = "Use copy instead", deprecatedSettersSince = "2.1.25") case class JvmCache(
  os: String = JvmChannel.defaultOsOrRaw(),
  architecture: String = JvmChannel.defaultArchitectureOrRaw(),
  defaultJdkNameOpt: Option[String] = Some(""), // empty value means use the default one for the passed os and architecure
  defaultVersionOpt: Option[String] = Some(JvmCache.defaultVersion),

  index: Option[Task[JvmIndex]] = None,

  handleLoggerLifecycle: Boolean = true,

  @since
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
    val artifact = Artifact(entry.url).copy(changing = entry.version.endsWith("SNAPSHOT"))
    getIfInstalled(artifact)
  }

  def getIfInstalled(artifact: Artifact): Task[Option[File]] =
    archiveCache.getIfExists(artifact).flatMap {
      case Left(e)          => Task.fail(e)
      case Right(None)      => Task.point(None)
      case Right(Some(dir)) => JvmCache.finalDirectory(artifact.url, dir, os).map(Some(_))
    }

  /** Gets the JVM of `entry`, downloading and extracting it if needed
    *
    * If `entry` has checksums, the strongest one coursier knows of is checked against the archive
    * before extracting it. The cache does that check, alongside the archive integrity check (see
    * `fileWithChecks` in `coursier.cache.FileCache`): if it fails, a `FileCache` removes the
    * archive and downloads it again once, before this fails with [[JvmCache.WrongArchiveChecksum]],
    * and a cache server does the same on its side.
    */
  def get(
    entry: JvmIndexEntry,
    logger: Option[JvmCacheLogger]
  ): Task[File] = {
    val artifact = Artifact(entry.url).copy(changing = entry.version.endsWith("SNAPSHOT"))
    JvmCache.checksumToCheck(entry.checksums) match {
      case None => get(artifact, logger)
      case Some((sumType, expected)) =>
        checkArchive(artifact, sumType, expected).flatMap { _ =>
          get(artifact, logger)
        }
    }
  }

  private def checkArchive(
    artifact: Artifact,
    sumType: String,
    expected: String
  ): Task[Unit] =
    archiveCache.cache
      .fileWithChecks(
        artifact,
        integrityCheck = archiveCache.integrityCheck.getOrElse(true),
        expectedChecksum = Some(ExpectedChecksum(sumType, expected))
      )(Task.sync)
      .run
      .flatMap {
        case Right(_) => Task.point(())
        case Left(err: ArtifactError.UnexpectedChecksum) =>
          Task.fail(
            new JvmCache.WrongArchiveChecksum(
              artifact.url,
              new File(err.file),
              err.sumType,
              err.expected,
              err.got
            )
          )
        case Left(err) => Task.fail(err)
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

  private lazy val defaultJdkNameOpt0 =
    defaultJdkNameOpt.map {
      case ""    => JvmCache.defaultJdkNameFor(os, architecture)
      case other => other
    }

  def entries(id: String): Task[Either[String, Seq[JvmIndexEntry]]] =
    JvmCache.idToNameVersion(id, defaultJdkNameOpt0, defaultVersionOpt) match {
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

  /** Uses `index` as the JVM index of this cache.
    *
    * `index` is memoized, so that it is run at most once per `JvmCache` instance: every lookup
    * method (`entries`, `get`, `getIfInstalled`, …) runs it, and loading and parsing the full index
    * each time makes iterating over many ids prohibitively expensive.
    */
  def withIndex(index: Task[JvmIndex]): JvmCache =
    copy(index = Some(index.memoize))

  def withIndex(indexUrl: String): JvmCache = {
    val indexTask = archiveCache.cache
      .loggerOpt
      .filter(_ => handleLoggerLifecycle)
      .getOrElse(CacheLogger.nop)
      .using {
        JvmIndex.load(
          archiveCache.cache,
          Nil,
          JvmChannel.FromUrl(indexUrl),
          Some(os),
          Some(architecture)
        )
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

  def withIndexChannel(repositories: Seq[Repository], indexChannel: JvmChannel): JvmCache =
    withIndexChannel(repositories, indexChannel, Some(os), Some(architecture))

  def withDefaultIndex: JvmCache = {
    val indexTask = archiveCache.cache
      .loggerOpt
      .filter(_ => handleLoggerLifecycle)
      .getOrElse(CacheLogger.nop)
      .using {
        JvmIndex.load(archiveCache.cache, os, architecture)
      }
    withIndex(indexTask)
  }

}

object JvmCache {

  def defaultJdkName: String =
    (JvmChannel.currentOs, JvmChannel.currentArchitecture) match {
      case (Right(os), Right(arch)) => defaultJdkNameFor(os, arch)
      case _                        => "temurin"
    }
  def defaultJdkNameFor(os: String, arch: String): String =
    // Seems zulu and liberica are the distributions
    // that support best Mac ARM and Windows ARM respectively,
    // and liberica is the only one still shipping recent JDKs for 32-bit x86
    if (os == "darwin" && arch == "arm64") "zulu"
    else if (os == "windows" && arch == "arm64") "liberica"
    else if (arch == "x86") "liberica"
    else "temurin"
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

  /** Checksum types of the JVM indices, from the strongest to the weakest, with the name of their
    * `MessageDigest` algorithm
    */
  private val checksumTypes = Seq(
    "sha512" -> "SHA-512",
    "sha256" -> "SHA-256",
    "sha1"   -> "SHA-1",
    "md5"    -> "MD5"
  )

  /** The strongest checksum among `checksums` that we know of, with its `MessageDigest` algorithm
    * name
    */
  private def checksumToCheck(checksums: Map[String, String]): Option[(String, String)] =
    if (checksums.isEmpty) None
    else {
      val normalized = checksums.map {
        case (k, v) =>
          k.toLowerCase(Locale.ROOT).filter(_ != '-') -> v
      }
      checksumTypes.collectFirst {
        case (key, algorithm) if normalized.contains(key) =>
          (algorithm, normalized(key))
      }
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

  final class WrongArchiveChecksum(
    val archiveUrl: String,
    val archive: File,
    val checksumType: String,
    val expected: String,
    val actual: String
  ) extends JvmCacheException(
        s"Wrong $checksumType checksum for $archive (from $archiveUrl): " +
          s"expected $expected, got $actual"
      )

}
