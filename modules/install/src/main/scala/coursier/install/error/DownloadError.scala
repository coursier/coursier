package coursier.install.error

final class DownloadError(val url: String, cause: Throwable = null)
    extends InstallDirException(s"Error downloading $url", cause)
