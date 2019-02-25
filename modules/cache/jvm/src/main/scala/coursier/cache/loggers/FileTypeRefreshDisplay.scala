package coursier.cache.loggers

import java.io.Writer
import java.util.Locale

import coursier.cache.internal.ConsoleDim

import scala.concurrent.duration.{Duration, DurationInt}

class FileTypeRefreshDisplay extends RefreshDisplay {

  import coursier.cache.internal.Terminal.Ansi

  val refreshInterval: Duration =
    20.millis

  private var currentHeight = 0

  private var done = Map.empty[String, RefreshInfo]
  private var ongoing = Map.empty[String, RefreshInfo]

  override def clear(out: Writer): Unit = {

    for (_ <- 1 to 2; _ <- 0 until currentHeight) {
      out.clearLine(2)
      out.down(1)
    }
    for (_ <- 0 until currentHeight)
      out.up(2)

    out.flush()

    done = Map.empty
    ongoing = Map.empty
    currentHeight = 0
  }

  private def truncatedPrintln(out: Writer, s: String, width: Int): Unit = {

    out.clearLine(2)

    if (s.length <= width)
      out.write(s + "\n")
    else
      out.write(s.take(width - 1) + "…\n")
  }

  private def extension(url: String): String = {
    val idx = url.lastIndexOf('.')
    if (idx < 0)
      "unknown"
    else
      url.substring(idx + 1)
  }

  private def excluded(url: String): Boolean =
    url.endsWith(".sha1") || url.endsWith(".sha256") || url.endsWith(".md5") || url.endsWith("/")

  def update(
    out: Writer,
    done0: Seq[(String, RefreshInfo)],
    downloads: Seq[(String, RefreshInfo)],
    changed: Boolean
  ): Unit =
    if (changed) {

      val width = ConsoleDim.width()

      done ++= done0

      ongoing = ongoing.filterNot { case (k, _) => done.contains(k) } ++ downloads

      var newHeight = 0

      if (done.nonEmpty) {
        val perExt = done
          .filter {
            case (url, _) =>
              !excluded(url)
          }
          .groupBy {
            case (url, _) =>
              extension(url)
          }
          .mapValues(_.size)
          .toVector
          .sortBy(-_._2)

        val line = perExt
          .map {
            case (ext, count) =>
              val ext0 =
                if (ext.length <= 3) ext.toUpperCase(Locale.ROOT)
                else ext
              s"$count $ext0 files"
          }
          .mkString("Downloaded ", ", ", "")

        truncatedPrintln(out, line, width)
        newHeight += 1
      }

      if (ongoing.nonEmpty) {

        val rate = ongoing
          .collect {
            case (_, info: RefreshInfo.DownloadInfo) =>
              info.rate().getOrElse(0.0)
          }
          .sum

        truncatedPrintln(out, s"Downloading at ${ProgressBarRefreshDisplay.byteCount(rate.toLong)} / s", width)
        newHeight += 1
      }

      if (newHeight < currentHeight) {
        for (_ <- newHeight until currentHeight) {
          out.clearLine(2)
          out.down(1)
        }

        for (_ <- newHeight until currentHeight)
          out.up(1)
      }

      for (_ <- 1 to newHeight)
        out.up(1)

      out.left(10000)

      out.flush()

      currentHeight = newHeight
    }

}

object FileTypeRefreshDisplay {

  def create(): FileTypeRefreshDisplay =
    new FileTypeRefreshDisplay

}
