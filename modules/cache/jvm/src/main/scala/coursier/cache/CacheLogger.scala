package coursier.cache

import java.io.File

import coursier.FileError

trait CacheLogger {
  def foundLocally(url: String, f: File): Unit = {}

  def downloadingArtifact(url: String, file: File): Unit = {}

  def downloadProgress(url: String, downloaded: Long): Unit = {}

  def downloadedArtifact(url: String, success: Boolean): Unit = {}
  def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
  def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit = {}

  def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {}

  def gettingLength(url: String): Unit = {}
  def gettingLengthResult(url: String, length: Option[Long]): Unit = {}

  def removedCorruptFile(url: String, file: File, reason: Option[FileError]): Unit = {}

  /**
    *
    * @param beforeOutput: called before any output is printed, iff something else is outputted.
    *                      (That is, if that `Logger` doesn't print any progress,
    *                      `initialMessage` won't be printed either.)
    */
  def init(beforeOutput: => Unit = ()): Unit = {}
  /**
    *
    * @return whether any message was printed by `Logger`
    */
  def stopDidPrintSomething(): Boolean = false
}

object CacheLogger {
  def nop: CacheLogger =
    new CacheLogger {}
}
