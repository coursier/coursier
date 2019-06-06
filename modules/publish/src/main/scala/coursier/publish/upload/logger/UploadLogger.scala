package coursier.publish.upload.logger

import coursier.publish.fileset.FileSet
import coursier.publish.upload.Upload.Error

trait UploadLogger {
  def uploadingSet(id: Object, fileSet: FileSet): Unit = ()
  def uploadedSet(id: Object, fileSet: FileSet): Unit = ()
  def uploading(url: String, idOpt: Option[Object], totalOpt: Option[Long]): Unit = ()
  def progress(url: String, idOpt: Option[Object], uploaded: Long, total: Long): Unit = ()
  def uploaded(url: String, idOpt: Option[Object], errorOpt: Option[Error]): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}
