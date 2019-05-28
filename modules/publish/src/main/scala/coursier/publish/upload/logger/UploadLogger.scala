package coursier.publish.upload.logger

import coursier.publish.fileset.FileSet
import coursier.publish.upload.Upload.Error

trait UploadLogger {
  def uploadingSet(id: Object, fileSet: FileSet): Unit = ()
  def uploadedSet(id: Object, fileSet: FileSet): Unit = ()
  def uploading(url: String, idOpt: Option[Object]): Unit =
    uploading(url)
  def uploaded(url: String, idOpt: Option[Object], errorOpt: Option[Error]): Unit =
    uploaded(url, errorOpt)

  def uploading(url: String): Unit = ()
  def uploaded(url: String, errorOpt: Option[Error]): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}
