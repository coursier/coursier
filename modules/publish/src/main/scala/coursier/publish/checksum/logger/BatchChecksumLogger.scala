package coursier.publish.checksum.logger

import java.io.PrintStream

import coursier.publish.checksum.ChecksumType

final class BatchChecksumLogger(out: PrintStream, verbosity: Int) extends ChecksumLogger {
  override def computing(id: Object, type0: ChecksumType, path: String): Unit =
    if (verbosity >= 0)
      out.println(s"Computing ${type0.name} checksum of ${path.repr}")
  override def computed(id: Object, type0: ChecksumType, path: String, errorOpt: Option[Throwable]): Unit = {
    if (verbosity >= 0)
      out.println(s"Computed ${type0.name} checksum of ${path.repr}")
  }
}
