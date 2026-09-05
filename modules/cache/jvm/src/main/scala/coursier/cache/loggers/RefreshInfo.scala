package coursier.cache.loggers

import coursier.cache.loggers.internal.RefreshInfoHelpers
import dataclass.{data, since => unroll}

sealed abstract class RefreshInfo extends Product with Serializable {
  def fraction: Option[Double]
  def watching: Boolean
  def success: Boolean
  def withSuccess(success: Boolean): RefreshInfo

  @deprecated("Call the override accepting an argument", "2.1.25")
  final def withSuccess(): RefreshInfo =
    withSuccess(true)
}

object RefreshInfo {

  @data case class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean,
    watching: Boolean,
    @unroll
    success: Boolean = true
  ) extends RefreshInfo with RefreshInfoHelpers.DownloadInfo {

    /** 0.0 to 1.0 */
    def fraction: Option[Double] = length.map(downloaded.toDouble / _)

    /** Byte / s */
    def rate(): Option[Double] = {
      val currentTime   = System.currentTimeMillis()
      val elapsed       = currentTime - startTime
      val netDownloaded = downloaded - previouslyDownloaded
      if (elapsed > 0 && netDownloaded > 0)
        Some(netDownloaded.toDouble / elapsed * 1000.0)
      else
        None
    }
  }

  @data case class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean,
    @unroll
    success: Boolean = true
  ) extends RefreshInfo with RefreshInfoHelpers.CheckUpdateInfo {
    def watching = false
    def fraction = None
  }

}
