package coursier.cache.loggers.internal

import coursier.cache.loggers.RefreshInfo

object RefreshInfoHelpers {
  trait DownloadInfo { self: RefreshInfo.DownloadInfo =>
    def withSuccess(success: Boolean): RefreshInfo.DownloadInfo =
      copy(success = success)
  }

  trait CheckUpdateInfo { self: RefreshInfo.CheckUpdateInfo =>
    def withSuccess(success: Boolean): RefreshInfo.CheckUpdateInfo =
      copy(success = success)
  }
}
