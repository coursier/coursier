package coursier.cache.loggers

import dataclass.{since => unroll}

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

  case class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean,
    watching: Boolean,
    @unroll
    success: Boolean = true
  ) extends RefreshInfo {

    def withSuccess(success: Boolean): RefreshInfo =
      copy(success = success)

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

  case class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean,
    @unroll
    success: Boolean = true
  ) extends RefreshInfo {
    def withSuccess(success: Boolean): RefreshInfo =
      copy(success = success)
    def watching = false
    def fraction = None
  }

}
