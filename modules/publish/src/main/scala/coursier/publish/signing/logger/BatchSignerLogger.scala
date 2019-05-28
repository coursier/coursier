package coursier.publish.signing.logger

import java.io.PrintStream

import coursier.publish.fileset.Path

final class BatchSignerLogger(out: PrintStream, verbosity: Int) extends SignerLogger {

  override def signingElement(id: Object, path: Path): Unit =
    if (verbosity >= 0)
      out.println(s"Signing ${path.repr}")
  override def signedElement(id: Object, path: Path, excOpt: Option[Throwable]): Unit =
    if (verbosity >= 0)
      out.println(s"Signed ${path.repr}")
}
