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
import dataclass.data
import org.codehaus.plexus.archiver.UnArchiver
import org.codehaus.plexus.logging.AbstractLogger

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
  index: Option[JvmIndex] = None,
  maxWaitDuration: Option[Duration] = Some(1.minute),
  durationBetweenChecks: FiniteDuration = 2.seconds,
  scheduledExecutor: Option[ScheduledExecutorService] = None
) {

  def withDefaultLogger(logger: JvmCacheLogger): JvmCache =
    withDefaultLogger(Some(logger))

  def get(
    entry: JvmIndexEntry,
    logger: Option[JvmCacheLogger],
    installIfNeeded: Boolean
  ): Task[File] = {
    val dir = directory(entry)
    val tmpDir = tempDirectory(dir)
    val logger0 = logger.orElse(defaultLogger).getOrElse(JvmCacheLogger.nop)

    Task.delay(Instant.now()).flatMap { initialAttempt =>

      lazy val task: Task[File] =
        Task.delay(dir.isDirectory).flatMap {
          case true => Task.point(dir)
          case false =>
            cache.file(Artifact(entry.url).withChanging(entry.version.endsWith("SNAPSHOT"))).run.flatMap {
              case Left(err) => Task.fail(err)
              case Right(archive) =>
                val tryExtract = Task.delay {
                  withLockFor(dir) {
                    logger0.extracting(entry.id, entry.url, dir)
                    val dir0 = try {

                      val unArchiver = entry.archiveType.unArchiver() : UnArchiver
                      unArchiver.setOverwrite(false)
                      unArchiver.setSourceFile(archive)
                      unArchiver.setDestDirectory(tmpDir)

                      JvmCache.deleteRecursive(tmpDir)
                      tmpDir.mkdirs()
                      unArchiver.extract()
                      val rootDir = tmpDir.listFiles().filter(!_.getName.startsWith(".")) match {
                        case Array() =>
                          throw new Exception(s"$archive is empty (from ${entry.url})")
                        case Array(rootDir0) =>
                          if (rootDir0.isDirectory)
                            rootDir0
                          else
                            throw new Exception(s"$archive does not contain a directory (from ${entry.url})")
                        case other =>
                          throw new Exception(s"Unexpected content at the root of $archive (from ${entry.url}): ${other.toSeq}")
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
                }

                tryExtract.flatMap {
                  case Some(dir) => Task.point(dir)
                  case None =>
                    maxWaitDuration match {
                      case None =>
                        Task.fail(new Exception(s"Directory $dir is locked"))
                      case Some(max) =>

                        val shouldTryAgainTask = Task.delay {
                          val now = Instant.now()
                          val elapsedMs = now.toEpochMilli - initialAttempt.toEpochMilli
                          elapsedMs < max.toMillis
                        }

                        shouldTryAgainTask.flatMap {
                          case false =>
                            Task.fail(new Exception(s"Directory $dir still locked after $max"))
                          case true =>
                            val executor = scheduledExecutor.getOrElse(JvmCache.defaultScheduledExecutor)
                            Task.completeAfter(executor, durationBetweenChecks)
                              .flatMap { _ => task }
                        }
                    }
                }
            }
        }

        task.map(JvmCache.finalDirectory(_, os))
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
    index match {
      case None => Task.fail(new Exception("No index specified"))
      case Some(index0) =>
        JvmCache.idToNameVersion(id, defaultJdkNameOpt, defaultVersionOpt) match {
          case None =>
            Task.fail(new Exception(s"Malformed JVM id '$id'"))
          case Some((name, ver)) =>
            index0.lookup(name, ver, Some(os), Some(architecture)) match {
              case Left(err) => Task.fail(new Exception(err))
              case Right(entry) =>
                get(entry)
            }
        }
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

  def directory(id: String): File = {
    assert(!id.contains("/"))
    new File(baseDirectory, id)
  }

  def installed(): Task[Seq[String]] =
    Task.delay {
      baseDirectory
        .listFiles()
        .filter { f =>
          !f.getName.startsWith(".") &&
            f.isDirectory
            // TODO Check that a java executable is there too?
        }
        .map(_.getName)
        .toVector
        .sorted
    }

  def withIndex(index: JvmIndex): JvmCache =
    withIndex(Some(index))

  def loadDefaultIndex: Task[JvmCache] =
    JvmIndex.load(cache)
      .map(withIndex(_))

}

object JvmCache {

  def default: Task[JvmCache] =
    JvmCache().loadDefaultIndex

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


  private[coursier] lazy val isMacOs: Boolean =
    Option(System.getProperty("os.name"))
      .map(_.toLowerCase(Locale.ROOT))
      .exists(_.contains("mac"))

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


  private lazy val defaultBaseDirectory0: File = {

    val fromProps = Option(System.getProperty("coursier.jvm-dir")).map(new File(_))
    def fromEnv = Option(System.getenv("COURSIER_JVM_DIR")).map(new File(_))
    def default = {
      val dataDir = CoursierPaths.dataLocalDirectory()
      new File(dataDir, "jvm")
    }

    fromProps
      .orElse(fromEnv)
      .getOrElse(default)
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

}
