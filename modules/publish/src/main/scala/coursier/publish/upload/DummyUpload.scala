package coursier.publish.upload

import coursier.core.Authentication
import coursier.publish.upload.logger.UploadLogger
import coursier.util.Task

final case class DummyUpload(underlying: Upload) extends Upload {
  def upload(url: String, authentication: Option[Authentication], content: Array[Byte], logger: UploadLogger): Task[Option[Upload.Error]] =
    Task.point(None)
}
