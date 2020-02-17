package coursier.cache

import coursier.util.{Sync, Task}

trait CacheLogger {
  def foundLocally(url: String): Unit = {}

  def downloadingArtifact(url: String): Unit = {}

  def downloadProgress(url: String, downloaded: Long): Unit = {}

  def downloadedArtifact(url: String, success: Boolean): Unit = {}
  def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
  def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit = {}

  def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {}

  def gettingLength(url: String): Unit = {}
  def gettingLengthResult(url: String, length: Option[Long]): Unit = {}

  def removedCorruptFile(url: String, reason: Option[String]): Unit = {}

  // sizeHint: estimated # of artifacts to be downloaded (doesn't include side stuff like checksums)
  def init(sizeHint: Option[Int] = None): Unit = {}
  def stop(): Unit = {}

  final def use[T](f: => T): T = {
    init()
    try f
    finally stop()
  }

  final def using[T]: CacheLogger.Using[T] =
    new CacheLogger.Using[T](this)
}

object CacheLogger {
  final class Using[T](logger: CacheLogger) {
    def apply[F[_]](task: F[T])(implicit sync: Sync[F]): F[T] =
      sync.bind(sync.delay(logger.init())) { _ =>
        sync.bind(sync.attempt(task)) { a =>
          sync.bind(sync.delay(logger.stop())) { _ =>
            sync.fromAttempt(a)
          }
        }
      }
  }
  def nop: CacheLogger =
    new CacheLogger {}
}
