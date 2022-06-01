package coursier.launcher.internal

import java.io.File

object Windows {

  def pathExtensions: Seq[String] =
    pathExtensions0

  private lazy val pathExtensions0 =
    Option(System.getenv("pathext"))
      .toSeq
      .flatMap(_.split(File.pathSeparator).toSeq)

}
