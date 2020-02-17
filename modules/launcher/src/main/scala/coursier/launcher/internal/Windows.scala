package coursier.launcher.internal

import java.io.File
import java.util.Locale

object Windows {

  def isWindows: Boolean =
    isWindows0

  def pathExtensions: Seq[String] =
    pathExtensions0


  private lazy val isWindows0: Boolean =
    Option(System.getProperty("os.name"))
      .map(_.toLowerCase(Locale.ROOT))
      .exists(_.contains("windows"))

  private lazy val pathExtensions0 =
    Option(System.getenv("pathext"))
      .toSeq
      .flatMap(_.split(File.pathSeparator).toSeq)

}
