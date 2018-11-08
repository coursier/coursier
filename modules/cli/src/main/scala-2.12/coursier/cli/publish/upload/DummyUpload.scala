package coursier.cli.publish.upload

import java.time.Instant

import coursier.core.Authentication
import coursier.util.Task

final case class DummyUpload(underlying: Upload) extends Upload {
  def exists(url: String, authentication: Option[Authentication], logger: Upload.Logger): Task[Boolean] =
    underlying.exists(url, authentication, logger)
  def downloadIfExists(url: String, authentication: Option[Authentication], logger: Upload.Logger): Task[Option[(Option[Instant], Array[Byte])]] =
    underlying.downloadIfExists(url, authentication, logger)

  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: Upload.Logger): Task[Option[Upload.Error]] =
    Task.point(None)
}
