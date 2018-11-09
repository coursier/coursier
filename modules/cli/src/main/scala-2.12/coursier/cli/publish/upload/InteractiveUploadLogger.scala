package coursier.cli.publish.upload

import java.io.{OutputStream, OutputStreamWriter, Writer}

import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import coursier.cli.publish.fileset.FileSet
import coursier.cli.publish.logging.ProgressLogger

// FIXME Would have been better if dummy was passed by the Upload instance when calling the methods of Upload.Logger
final class InteractiveUploadLogger(out: Writer, dummy: Boolean, isLocal: Boolean) extends Upload.Logger {

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
    doneEmoji = emoji("truck").map(_.toString())
  )

  override def uploadingSet(id: Object, fileSet: FileSet): Unit =
    underlying.processingSet(id, Some(fileSet.elements.length))
  override def uploadedSet(id: Object, fileSet: FileSet): Unit =
    underlying.processedSet(id)

  override def uploading(url: String, idOpt: Option[Object]): Unit =
    for (id <- idOpt)
      underlying.processing(url, id)
  override def uploaded(url: String, idOpt: Option[Object], errorOpt: Option[Upload.Error]): Unit =
    for (id <- idOpt)
      underlying.processed(url, id, errorOpt.nonEmpty)

  override def start(): Unit =
    underlying.start()
  override def stop(keep: Boolean): Unit =
    underlying.stop(keep)
}

object InteractiveUploadLogger {
  def create(out: OutputStream, dummy: Boolean, isLocal: Boolean): Upload.Logger =
    new InteractiveUploadLogger(new OutputStreamWriter(out), dummy, isLocal)
}
