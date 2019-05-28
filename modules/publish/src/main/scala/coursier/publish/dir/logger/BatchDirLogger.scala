package coursier.publish.dir.logger

import java.io.PrintStream
import java.nio.file.Path

final class BatchDirLogger(out: PrintStream, dirName: String, verbosity: Int) extends DirLogger {

  override def reading(dir: Path): Unit =
    if (verbosity >= 0)
      out.println(s"Reading $dirName")
  override def element(dir: Path, file: Path): Unit =
    if (verbosity >= 0)
      out.println(s"Found $file")
  override def read(dir: Path, elements: Int): Unit =
    if (verbosity >= 0)
      out.println(s"Found $elements elements in $dirName")
}
