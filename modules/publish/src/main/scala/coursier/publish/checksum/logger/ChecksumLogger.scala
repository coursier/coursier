package coursier.publish.checksum.logger

import coursier.publish.checksum.ChecksumType
import coursier.publish.fileset.FileSet

trait ChecksumLogger {
  def computingSet(id: Object, fs: FileSet): Unit = ()
  def computing(id: Object, type0: ChecksumType, path: String): Unit = ()
  def computed(id: Object, type0: ChecksumType, path: String, errorOpt: Option[Throwable]): Unit = ()
  def computedSet(id: Object, fs: FileSet): Unit = ()

  def start(): Unit = ()
  def stop(keep: Boolean = true): Unit = ()
}
