package coursier.cache

import java.io.File
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.{Callable, ConcurrentHashMap}

import coursier.paths.CachePath

import scala.annotation.tailrec
import java.nio.channels.FileChannel

object CacheLocks {

  /**
    * Should be acquired when doing operations changing the file structure of the cache (creating
    * new directories, creating / acquiring locks, ...), so that these don't hinder each other.
    *
    * Should hopefully address some transient errors seen on the CI of ensime-server.
    */
  def withStructureLock[T](cache: File)(f: => T): T =
    CachePath.withStructureLock(cache, new Callable[T] { def call() = f })

  def withStructureLock[T](cache: Path)(f: => T): T =
    CachePath.withStructureLock(cache, new Callable[T] { def call() = f })

  def withLockOr[T](
    cache: Path,
    file: Path
  )(
    f: => T,
    ifLocked: => Option[T]
  ): T = {

    val lockFile = CachePath.lockFile(file)

    var channel: FileChannel = null

    withStructureLock(cache) {
      Files.createDirectories(lockFile.getParent)
      channel = FileChannel.open(
        lockFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE
      )
    }

    @tailrec
    def loop(): T = {

      val resOpt = {
        var lock: FileLock = null
        try {
          lock = channel.tryLock()
          if (lock == null)
            ifLocked
          else
            try Some(f)
            finally {
              lock.release()
              lock = null
              channel.close()
              channel = null
            }
        }
        catch {
          case _: OverlappingFileLockException =>
            ifLocked
        }
        finally {
          if (lock != null)
            lock.release()
        }
      }

      resOpt match {
        case Some(res) => res
        case None =>
          loop()
      }
    }

    try loop()
    finally {
      if (channel != null)
        channel.close()
    }
  }

  def withLockFor[T](cache: Path, file: Path)(f: => Either[ArtifactError, T]): Either[ArtifactError, T] =
    withLockOr(cache, file)(f, Some(Left(new ArtifactError.Locked(file))))

  private val urlLocks = new ConcurrentHashMap[String, Object]
  private val urlLockDummyObject = new Object

  def withUrlLock[T](url: String)(f: => T): Option[T] = {

    val prev = urlLocks.putIfAbsent(url, urlLockDummyObject)

    if (prev == null)
      try Some(f)
      finally {
        urlLocks.remove(url)
      }
    else
      None
  }

}
