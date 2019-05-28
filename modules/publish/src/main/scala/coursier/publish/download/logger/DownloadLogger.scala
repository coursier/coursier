package coursier.publish.download.logger

trait DownloadLogger {

  def checking(url: String): Unit = ()
  def checked(url: String, exists: Boolean, errorOpt: Option[Throwable]): Unit = ()

  def downloadingIfExists(url: String): Unit = ()
  def downloadedIfExists(url: String, size: Option[Long], errorOpt: Option[Throwable]): Unit = ()

}
