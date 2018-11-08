package coursier.cli.publish.upload

import java.io.PrintStream

final class SimpleDownloadLogger(out: PrintStream, verbosity: Int) extends Upload.Logger {

  override def downloadingIfExists(url: String): Unit = {
    if (verbosity >= 2)
      out.println(s"Trying to download $url")
  }

  override def downloadedIfExists(url: String, size: Option[Long], errorOpt: Option[Throwable]): Unit =
    if (verbosity >= 2) {
      val msg =
        if (size.isEmpty)
          s"Not found : $url (ignored)"
        else
          s"Downloaded $url"
      out.println(msg)
    } else if (verbosity >= 1) {
      if (size.nonEmpty)
        out.println(s"Downloaded $url")
    }

}
