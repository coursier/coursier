package coursier.cache

import coursier.util.{Artifact, Sync, Task}
import coursier.util.Monad.ops._

trait CacheLogger {
  def foundLocally(url: String): Unit = {}

  def checkingArtifact(url: String, artifact: Artifact): Unit = {}

  // now deprecated, override / call the downloadingArtifact method with 2 arguments instead
  def downloadingArtifact(url: String): Unit = {}

  // We may have artifact.url != url. In that case, url should be the URL of a checksum of artifact.
  def downloadingArtifact(url: String, artifact: Artifact): Unit =
    downloadingArtifact(url)

  def downloadProgress(url: String, downloaded: Long): Unit = {}

  def downloadedArtifact(url: String, success: Boolean): Unit          = {}
  def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
  def checkingUpdatesResult(
    url: String,
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long]
  ): Unit = {}

  def downloadLength(
    url: String,
    totalLength: Long,
    alreadyDownloaded: Long,
    watching: Boolean
  ): Unit = {}

  def gettingLength(url: String): Unit                             = {}
  def gettingLengthResult(url: String, length: Option[Long]): Unit = {}

  def removedCorruptFile(url: String, reason: Option[String]): Unit = {}

  // FIXME Create another logger class for that?
  def pickedModuleVersion(module: String, version: String): Unit = {}

  // sizeHint: estimated # of artifacts to be downloaded (doesn't include side stuff like checksums)
  def init(sizeHint: Option[Int] = None): Unit = {}
  def stop(): Unit                             = {}

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
      for {
        _ <- sync.delay(logger.init())
        a <- sync.attempt(task)
        _ <- sync.delay(logger.stop())
        t <- sync.fromAttempt(a)
      } yield t
  }
  def nop: CacheLogger =
    new CacheLogger {}
}
