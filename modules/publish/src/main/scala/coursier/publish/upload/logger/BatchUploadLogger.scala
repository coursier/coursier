package coursier.publish.upload.logger

import java.io.PrintStream

import coursier.publish.fileset.FileSet
import coursier.publish.upload.Upload

final class BatchUploadLogger(out: PrintStream, dummy: Boolean, isLocal: Boolean) extends UploadLogger {

  private val processing =
    if (isLocal) {
      if (dummy)
        "Would have tried to write"
      else
        "Writing"
    } else {
      if (dummy)
        "Would have tried to upload"
      else
        "Uploading"
    }

  override def uploadingSet(id: Object, fileSet: FileSet): Unit =
    out.println(s"$processing ${fileSet.elements.length} files")

  override def uploading(url: String, idOpt: Option[Object]): Unit =
    out.println(s"Uploading $url")
  override def uploaded(url: String, idOpt: Option[Object], errorOpt: Option[Upload.Error]): Unit =
    errorOpt match {
      case None =>
        out.println(s"Uploaded $url")
      case Some(err) =>
        out.println(s"Failed to upload $url: $err")
    }
}
