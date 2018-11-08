package coursier.cli.publish.checksum

import coursier.cli.publish.fileset.FileSet

trait ChecksumLogger {
  def computingSet(id: Object, fs: FileSet): Unit = ()
  def computing(id: Object, type0: ChecksumType, path: String): Unit = ()
  def computed(id: Object, type0: ChecksumType, path: String, errorOpt: Option[Throwable]): Unit = ()
  def computedSet(id: Object, fs: FileSet): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}
