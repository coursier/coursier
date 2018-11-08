package coursier.cli.publish.signing

import coursier.cli.publish.fileset.FileSet

trait SignerLogger {
  def signing(id: Object, fs: FileSet): Unit = ()
  def signingElement(id: Object, path: FileSet.Path): Unit = ()
  def signedElement(id: Object, path: FileSet.Path, excOpt: Option[Throwable]): Unit = ()
  def signed(id: Object, fs: FileSet): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}

object SignerLogger {

  val nop: SignerLogger =
    new SignerLogger {}

}
