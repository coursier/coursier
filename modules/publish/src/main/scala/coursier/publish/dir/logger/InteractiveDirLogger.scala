package coursier.publish.dir.logger

import java.io.{OutputStream, OutputStreamWriter}
import java.nio.file.Path

import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import coursier.publish.logging.ProgressLogger

final class InteractiveDirLogger(out: OutputStreamWriter, dirName: String, verbosity: Int) extends DirLogger {

  private val underlying = new ProgressLogger[String](
    "Read",
    s"files from $dirName",
    out,
    doneEmoji = emoji("mag").map(_.toString())
  )

  override def reading(dir: Path): Unit =
    underlying.processingSet(dirName, None)
  override def element(dir: Path, file: Path): Unit = {
    underlying.processing(file.toString, dirName)
    underlying.processed(file.toString, dirName, false)
  }
  override def read(dir: Path, elements: Int): Unit =
    underlying.processedSet(dirName)

  override def start(): Unit =
    underlying.start()
  override def stop(keep: Boolean): Unit =
    underlying.stop(keep)
}

object InteractiveDirLogger {
  def create(out: OutputStream, dirName: String, verbosity: Int): DirLogger =
    new InteractiveDirLogger(new OutputStreamWriter(out), dirName, verbosity)
}
