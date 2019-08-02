package coursier.cache.loggers

import java.io.Writer
import java.util.Locale

import scala.concurrent.duration.Duration

trait RefreshDisplay {

  // note about concurrency: newEntry / removeEntry may be called concurrently to update, and the update arguments
  // may be out-of-sync with them
  def newEntry(out: Writer, url: String, info: RefreshInfo): Unit = ()
  def removeEntry(out: Writer, url: String, info: RefreshInfo): Unit = ()

  def sizeHint(n: Int): Unit = ()
  def update(
    out: Writer,
    done: Seq[(String, RefreshInfo)],
    downloads: Seq[(String, RefreshInfo)],
    changed: Boolean
  ): Unit
  def stop(out: Writer): Unit = ()

  def refreshInterval: Duration

}

object RefreshDisplay {

  private lazy val isWindows: Boolean =
    sys.props
      .get("os.name")
      .map(_.toLowerCase(Locale.ROOT))
      .exists(_.contains("windows"))

  def truncated(s: String, width: Int): String =
    if (s.length <= width)
      s
    else if (isWindows)
      // seems unicode character '…' isn't fine in Windows terminal, plus width is actually shorter (scrollbar?)
      s.take(width - 4) + "..."
    else
      s.take(width - 1) + "…"

}
