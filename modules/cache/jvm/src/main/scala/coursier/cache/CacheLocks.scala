package coursier.cache

import java.io.{File, FileOutputStream}
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.util.concurrent.{Callable, ConcurrentHashMap}

import coursier.FileError
import coursier.paths.CachePath

import scala.annotation.tailrec

object CacheLocks {

  /**
    * Should be acquired when doing operations changing the file structure of the cache (creating
    * new directories, creating / acquiring locks, ...), so that these don't hinder each other.
    *
    * Should hopefully address some transient errors seen on the CI of ensime-server.
    */
  def withStructureLock[T](cache: File)(f: => T): T =
    CachePath.withStructureLock(cache, new Callable[T] { def call() = f })

  def withLockOr[T](
    cache: File,
    file: File
  )(
    f: => Either[FileError, T],
    ifLocked: => Option[Either[FileError, T]]
  ): Either[FileError, T] = {

    val lockFile = CachePath.lockFile(file)

    var out: FileOutputStream = null

    withStructureLock(cache) {
      lockFile.getParentFile.mkdirs()
      out = new FileOutputStream(lockFile)
    }

    @tailrec
    def loop(): Either[FileError, T] = {

      val resOpt = {
        var lock: FileLock = null
        try {
          lock = out.getChannel.tryLock()
          if (lock == null)
            ifLocked
          else
            try Some(f)
            finally {
              lock.release()
              lock = null
              out.close()
              out = null
              lockFile.delete()
            }
        } catch {
          case _: OverlappingFileLockException =>
            ifLocked
        } finally if (lock != null) lock.release()
      }

      resOpt match {
        case Some(res) => res
        case None =>
          loop()
      }
    }

    try loop()
    finally if (out != null) out.close()
  }

  def withLockFor[T](cache: File, file: File)(f: => Either[FileError, T]): Either[FileError, T] =
    withLockOr(cache, file)(f, Some(Left(FileError.Locked(file))))

  private val urlLocks = new ConcurrentHashMap[String, Object]
  private val urlLockDummyObject = new Object

  def withUrlLock[T](url: String)(f: => T): Option[T] = {

    val prev = urlLocks.putIfAbsent(url, urlLockDummyObject)

    if (prev == null)
      try Some(f)
      finally {
        urlLocks.remove(url)
      } else
      None
  }

}
