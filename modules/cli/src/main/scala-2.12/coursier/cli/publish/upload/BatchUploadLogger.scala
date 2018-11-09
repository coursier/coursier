package coursier.cli.publish.upload

import java.io.PrintStream

import coursier.cli.publish.fileset.FileSet

final class BatchUploadLogger(out: PrintStream, dummy: Boolean, isLocal: Boolean) extends Upload.Logger {

  private val processing =
    if (isLocal) {
      if (dummy)
        "Would have written"
      else
        "Wrote"
    } else {
      if (dummy)
        "Would have uploaded"
      else
        "Uploaded"
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
