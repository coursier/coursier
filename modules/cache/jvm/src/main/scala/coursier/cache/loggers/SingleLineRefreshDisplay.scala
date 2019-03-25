package coursier.cache.loggers

import java.io.Writer
import java.sql.Timestamp

import coursier.cache.internal.ConsoleDim

import scala.concurrent.duration.{Duration, DurationInt}

class SingleLineRefreshDisplay(
  beforeOutput: => Unit,
  afterOutput: => Unit
) extends RefreshDisplay {

  import coursier.cache.internal.Terminal.Ansi
  import SingleLineRefreshDisplay.display

  val refreshInterval: Duration =
    20.millis

  private var printedAnything0 = false
  private var currentHeight = 0

  override def stop(out: Writer): Unit = {

    for (_ <- 1 to 2; _ <- 0 until currentHeight) {
      out.clearLine(2)
      out.down(1)
    }
    for (_ <- 0 until currentHeight)
      out.up(2)

    out.flush()

    if (printedAnything0) {
      afterOutput
      printedAnything0 = false
    }

    currentHeight = 0
  }

  private def truncatedPrintln(out: Writer, s: String, width: Int): Unit = {

    out.clearLine(2)

    if (s.length <= width)
      out.write(s + "\n")
    else
      out.write(s.take(width - 1) + "…\n")
  }

  def update(
    out: Writer,
    done: Seq[(String, RefreshInfo)],
    downloads: Seq[(String, RefreshInfo)],
    changed: Boolean
  ): Unit =
    if (changed) {

      val width = ConsoleDim.width()

      val done0 = done
        .filter {
          case (url, _) =>
            !url.endsWith(".sha1") && !url.endsWith(".sha256") && !url.endsWith(".md5") && !url.endsWith("/")
        }

      for (((url, info), isDone) <- done0.iterator.map((_, true)) ++ downloads.iterator.map((_, false))) {
        assert(info != null, s"Incoherent state ($url)")

        if (!printedAnything0) {
          beforeOutput
          printedAnything0 = true
        }

        truncatedPrintln(out, url, width)
        out.clearLine(2)
        out.write(s"  ${display(info, isDone)}\n")
      }

      val displayedCount = (done0 ++ downloads).length

      if (displayedCount < currentHeight) {
        for (_ <- 1 to 2; _ <- displayedCount until currentHeight) {
          out.clearLine(2)
          out.down(1)
        }

        for (_ <- displayedCount until currentHeight)
          out.up(2)
      }

      for (_ <- downloads.indices)
        out.up(2)

      out.left(10000)

      out.flush()

      currentHeight = downloads.length
    }

}

object SingleLineRefreshDisplay {

  def create(): SingleLineRefreshDisplay =
    new SingleLineRefreshDisplay((), ())

  def create(
    beforeOutput: => Unit,
    afterOutput: => Unit
  ): SingleLineRefreshDisplay =
    new SingleLineRefreshDisplay(beforeOutput, afterOutput)


  // Scala version of http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java/3758880#3758880
  def byteCount(bytes: Long, si: Boolean = false) = {
    val unit = if (si) 1000 else 1024
    if (bytes < unit)
      bytes + " B"
    else {
      val prefixes = if (si) "kMGTPE" else "KMGTPE"
      val exp = (math.log(bytes) / math.log(unit)).toInt min prefixes.length
      val pre = prefixes.charAt(exp - 1) + (if (si) "" else "i")
      f"${bytes / math.pow(unit, exp)}%.1f ${pre}B"
    }
  }

  private val format =
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private def formatTimestamp(ts: Long): String =
    format.format(new Timestamp(ts))

  private def display(info: RefreshInfo, isDone: Boolean): String = {

    info match {
      case d: RefreshInfo.DownloadInfo =>

        val actualFraction = d.fraction
          .orElse(if (isDone) Some(1.0) else None)
          .orElse(if (d.downloaded == 0L) Some(0.0) else None)

        val start =
          actualFraction match {
            case None =>
              "       [          ] "
            case Some(frac) =>
              val elem = if (d.watching) "." else "#"

              val decile = (10.0 * frac).toInt
              assert(decile >= 0)
              assert(decile <= 10)

              f"${100.0 * frac}%5.1f%%" +
                " [" + (elem * decile) + (" " * (10 - decile)) + "] "
          }

        start +
          byteCount(d.downloaded) +
          d.rate().fold("")(r => s" (${byteCount(r.toLong)} / s)")

      case c: RefreshInfo.CheckUpdateInfo =>

        if (isDone)
          (c.currentTimeOpt, c.remoteTimeOpt) match {
            case (Some(current), Some(remote)) =>
              if (current < remote)
                s"Updated since ${formatTimestamp(current)} (${formatTimestamp(remote)})"
              else if (current == remote)
                s"No new update since ${formatTimestamp(current)}"
              else
                s"Warning: local copy newer than remote one (${formatTimestamp(current)} > ${formatTimestamp(remote)})"
            case (Some(_), None) =>
              // FIXME Likely a 404 Not found, that should be taken into account by the cache
              "No modified time in response"
            case (None, Some(remote)) =>
              s"Last update: ${formatTimestamp(remote)}"
            case (None, None) =>
              "" // ???
          }
        else
          c.currentTimeOpt match {
            case Some(current) =>
              s"Checking for updates since ${formatTimestamp(current)}"
            case None =>
              "" // ???
          }
    }
  }

}
