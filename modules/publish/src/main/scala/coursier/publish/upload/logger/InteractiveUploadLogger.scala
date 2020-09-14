package coursier.publish.upload.logger

import java.io.{OutputStream, OutputStreamWriter, Writer}

import coursier.publish.fileset.FileSet
import coursier.publish.logging.ProgressLogger
import coursier.publish.upload.Upload

// FIXME Would have been better if dummy was passed by the Upload instance when calling the methods of UploadLogger
final class InteractiveUploadLogger(out: Writer, dummy: Boolean, isLocal: Boolean) extends UploadLogger {

  private val underlying = new ProgressLogger[Object](
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
    },
    "files",
    out,
    doneEmoji = Some("\ud83d\ude9a")
  )

  override def uploadingSet(id: Object, fileSet: FileSet): Unit =
    underlying.processingSet(id, Some(fileSet.elements.length))
  override def uploadedSet(id: Object, fileSet: FileSet): Unit =
    underlying.processedSet(id)

  override def uploading(url: String, idOpt: Option[Object], totalOpt: Option[Long]): Unit =
    for (id <- idOpt)
      underlying.processing(url, id)

  override def progress(url: String, idOpt: Option[Object], uploaded: Long, total: Long): Unit =
    for (id <- idOpt)
      underlying.progress(url, id, uploaded, total)
  override def uploaded(url: String, idOpt: Option[Object], errorOpt: Option[Upload.Error]): Unit =
    for (id <- idOpt)
      underlying.processed(url, id, errorOpt.nonEmpty)

  override def start(): Unit =
    underlying.start()
  override def stop(keep: Boolean): Unit =
    underlying.stop(keep)
}

object InteractiveUploadLogger {
  def create(out: OutputStream, dummy: Boolean, isLocal: Boolean): UploadLogger =
    new InteractiveUploadLogger(new OutputStreamWriter(out), dummy, isLocal)
}
