package coursier.cache.loggers

import java.io.Writer

import coursier.cache.loggers.RefreshInfo.{CheckUpdateInfo, DownloadInfo}

import scala.concurrent.duration.{Duration, DurationInt}

class FallbackRefreshDisplay(quiet: Boolean = false) extends RefreshDisplay {

  private var previous = Set.empty[String]
  @volatile private var lastInstantOpt = Option.empty[Long]

  private def describe(info: RefreshInfo): String =
    info match {
      case downloadInfo: DownloadInfo =>
        val pctOpt = downloadInfo.fraction.map(100.0 * _)

        if (downloadInfo.length.isEmpty && downloadInfo.downloaded == 0L)
          ""
        else
          s"(${pctOpt.map(pct => f"$pct%.2f %%, ").mkString}${downloadInfo.downloaded}${downloadInfo.length.map(" / " + _).mkString})"

      case _: CheckUpdateInfo =>
        "Checking for updates"
    }

  val refreshInterval: Duration =
    1.second

  override def newEntry(out: Writer, url: String, info: RefreshInfo): Unit = {
    lastInstantOpt = Some(System.currentTimeMillis())

    if (!quiet) {
      val msg = info match {
        case _: DownloadInfo =>
          s"Downloading $url\n"
        case _: CheckUpdateInfo =>
          s"Checking $url\n"
      }
      out.write(msg)
      out.flush()
    }
  }

  override def removeEntry(out: Writer, url: String, info: RefreshInfo): Unit = {
    lastInstantOpt = Some(System.currentTimeMillis())

    if (!quiet) {
      val prefix = if (info.watching) "(watching) " else ""
      val msg = info match {
        case _: DownloadInfo =>
          s"Downloaded $url\n"
        case _: CheckUpdateInfo =>
          s"Checked $url\n"
      }

      out.write(prefix + msg)
      out.flush()
    }
  }

  def update(
    out: Writer,
    done: Seq[(String, RefreshInfo)],
    downloads: Seq[(String, RefreshInfo)],
    changed: Boolean
  ): Unit = {

    val now = System.currentTimeMillis()

    // displaying updates if last message is more than 5 s old
    if (lastInstantOpt.exists(now > _ + 5000L)) {
      val downloads0 = downloads.filter { case (url, _) => previous(url) }
      if (downloads0.nonEmpty) {
        out.write("Still downloading:" + System.lineSeparator())
        for ((url, info) <- downloads0) {
          assert(info != null, s"Incoherent state ($url)")
          out.write(s"$url ${describe(info)}" + System.lineSeparator())
        }

        out.write(System.lineSeparator())

        out.flush()
        lastInstantOpt = Some(now)
      }
    }

    previous = previous ++ downloads.map(_._1)
  }
  override def stop(out: Writer): Unit = {
    previous = Set.empty
    lastInstantOpt = None
  }
}
