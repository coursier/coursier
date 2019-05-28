package coursier.publish.signing.logger

import coursier.publish.fileset.{FileSet, Path}

trait SignerLogger {
  def signing(id: Object, fs: FileSet): Unit = ()
  def signingElement(id: Object, path: Path): Unit = ()
  def signedElement(id: Object, path: Path, excOpt: Option[Throwable]): Unit = ()
  def signed(id: Object, fs: FileSet): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}

object SignerLogger {

  val nop: SignerLogger =
    new SignerLogger {}

}
