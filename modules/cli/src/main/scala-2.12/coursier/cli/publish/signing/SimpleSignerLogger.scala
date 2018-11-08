package coursier.cli.publish.signing

import java.io.{OutputStream, OutputStreamWriter, Writer}

import coursier.cli.publish.fileset.FileSet
import coursier.cli.publish.logging.ProgressLogger

final class SimpleSignerLogger(out: Writer, verbosity: Int) extends SignerLogger {

  private val underlying = new ProgressLogger[Object](
    "Signed",
    "files",
    out,
    updateOnChange = true,
    doneEmoji = Some("\u270D\uFE0F ")
  )

  override def signing(id: Object, fileSet: FileSet): Unit = {
    underlying.processingSet(id, Some(fileSet.elements.length))
  }
  override def signed(id: Object, fileSet: FileSet): Unit =
    underlying.processedSet(id)

  override def signingElement(id: Object, path: FileSet.Path): Unit = {
    if (verbosity >= 2)
      out.write(s"Signing ${path.repr}\n")
    underlying.processing(path.repr, id)
  }
  override def signedElement(id: Object, path: FileSet.Path, excOpt: Option[Throwable]): Unit = {
    if (verbosity >= 2)
      out.write(s"Signed ${path.repr}\n")
    underlying.processed(path.repr, id, excOpt.nonEmpty)
  }

  override def start(): Unit =
    underlying.start()
  override def stop(keep: Boolean): Unit =
    underlying.stop(keep)
}

object SimpleSignerLogger {
  def create(out: OutputStream, verbosity: Int): SignerLogger =
    new SimpleSignerLogger(new OutputStreamWriter(out), verbosity)
}
