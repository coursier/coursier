package coursier.publish.checksum.logger

import java.io.{OutputStream, OutputStreamWriter, Writer}

import coursier.publish.checksum.ChecksumType
import coursier.publish.fileset.FileSet
import coursier.publish.logging.ProgressLogger

final class InteractiveChecksumLogger(out: Writer, verbosity: Int) extends ChecksumLogger {

  private val underlying = new ProgressLogger[Object](
    "Computed",
    "checksums",
    out
  )

  override def computingSet(id: Object, fs: FileSet): Unit =
    underlying.processingSet(id, Some(fs.elements.length))
  override def computing(id: Object, type0: ChecksumType, path: String): Unit = {
    if (verbosity >= 2)
      out.write(s"Computing ${type0.name} checksum of ${path.repr}\n")
    underlying.processing(path, id)
  }
  override def computed(id: Object, type0: ChecksumType, path: String, errorOpt: Option[Throwable]): Unit = {
    if (verbosity >= 2)
      out.write(s"Computed ${type0.name} checksum of ${path.repr}\n")
    underlying.processed(path, id, errorOpt.nonEmpty)
  }
  override def computedSet(id: Object, fs: FileSet): Unit =
    underlying.processedSet(id)

  override def start(): Unit =
    underlying.start()
  override def stop(keep: Boolean): Unit =
    underlying.stop(keep)
}

object InteractiveChecksumLogger {
  def create(out: OutputStream, verbosity: Int): InteractiveChecksumLogger =
    new InteractiveChecksumLogger(new OutputStreamWriter(out), verbosity)
}
