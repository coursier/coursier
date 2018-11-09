package coursier.cli.publish.signing

import java.io.PrintStream

import coursier.cli.publish.fileset.FileSet

final class BatchSignerLogger(out: PrintStream, verbosity: Int) extends SignerLogger {

  override def signingElement(id: Object, path: FileSet.Path): Unit =
    if (verbosity >= 0)
      out.println(s"Signing ${path.repr}")
  override def signedElement(id: Object, path: FileSet.Path, excOpt: Option[Throwable]): Unit =
    if (verbosity >= 0)
      out.println(s"Signed ${path.repr}")
}
