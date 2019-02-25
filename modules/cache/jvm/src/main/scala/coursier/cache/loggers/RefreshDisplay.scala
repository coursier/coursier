package coursier.cache.loggers

import java.io.Writer

import scala.concurrent.duration.Duration

trait RefreshDisplay {

  // note about concurrency: newEntry / removeEntry may be called concurrently to update, and the update arguments
  // may be out-of-sync with them
  def newEntry(out: Writer, url: String, info: RefreshInfo): Unit = ()
  def removeEntry(out: Writer, url: String, info: RefreshInfo): Unit = ()

  def update(
    out: Writer,
    done: Seq[(String, RefreshInfo)],
    downloads: Seq[(String, RefreshInfo)],
    changed: Boolean
  ): Unit
  def clear(out: Writer): Unit = ()

  def refreshInterval: Duration

}
