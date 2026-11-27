package coursier.cli.internal

import coursier.cache.CacheLogger
import coursier.util.Artifact

/** A minimal [[CacheLogger]] writing one line per event to `System.err`.
  *
  * `coursier.cache.loggers` is JVM-only (its progress bars need `Terminal` / `ConsoleDim`, which
  * are backed by coursier's Windows JNI helpers), so this stands in for `FallbackRefreshDisplay` -
  * the display the JVM launcher itself falls back to when the output is not a terminal - and prints
  * the same messages.
  */
final class LineLogger(logChanging: Boolean, logPickedVersions: Boolean) extends CacheLogger {

  private def log(message: String): Unit =
    synchronized {
      System.err.println(message)
      System.err.flush()
    }

  override def checkingArtifact(url: String, artifact: Artifact): Unit =
    if (logChanging && artifact.changing)
      log(s"Checking changing artifact $url")

  override def pickedModuleVersion(module: String, version: String): Unit =
    if (logPickedVersions)
      log(s"Using $module:$version")

  override def downloadingArtifact(url: String, artifact: Artifact): Unit =
    log(s"Downloading $url")

  override def downloadedArtifact(url: String, success: Boolean): Unit =
    if (success) log(s"Downloaded $url")
    else log(s"Failed to download $url")

  override def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit =
    log(s"Checking $url")

  override def checkingUpdatesResult(
    url: String,
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long]
  ): Unit =
    log(s"Checked $url")

  override def removedCorruptFile(url: String, reason: Option[String]): Unit =
    log(s"Removing corrupt file $url${reason.map(" (" + _ + ")").mkString}")
}
