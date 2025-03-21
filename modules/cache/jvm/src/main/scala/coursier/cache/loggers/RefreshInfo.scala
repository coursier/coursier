package coursier.cache.loggers

import dataclass.{data, since}

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

  @data class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean,
    watching: Boolean,
    @since("2.1.25")
    success: Boolean = true
  ) extends RefreshInfo {

    /** 0.0 to 1.0 */
    def fraction: Option[Double] = length.map(downloaded.toDouble / _)

    /** Byte / s */
    def rate(): Option[Double] = {
      val currentTime = System.currentTimeMillis()
      if (currentTime > startTime)
        Some(
          (downloaded - previouslyDownloaded).toDouble / (System.currentTimeMillis() - startTime) * 1000.0
        )
      else
        None
    }
  }

  @data class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean,
    @since("2.1.25")
    success: Boolean = true
  ) extends RefreshInfo {
    def watching = false
    def fraction = None
  }

}
