package coursier.cache.loggers

sealed abstract class RefreshInfo extends Product with Serializable {
  def fraction: Option[Double]
  def watching: Boolean
}

object RefreshInfo {

  final case class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean,
    watching: Boolean
  ) extends RefreshInfo {
    /** 0.0 to 1.0 */
    def fraction: Option[Double] = length.map(downloaded.toDouble / _)
    /** Byte / s */
    def rate(): Option[Double] = {
      val currentTime = System.currentTimeMillis()
      if (currentTime > startTime)
        Some((downloaded - previouslyDownloaded).toDouble / (System.currentTimeMillis() - startTime) * 1000.0)
      else
        None
    }
  }

  final case class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean
  ) extends RefreshInfo {
    def watching = false
    def fraction = None
  }

}
