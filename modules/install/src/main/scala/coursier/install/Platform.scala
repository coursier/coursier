package coursier.install

import java.util.Locale

object Platform {

  def get(os: String, arch: String): Option[String] = {

    val os0   = os.toLowerCase(Locale.ROOT)
    val arch0 = if (arch == "amd64") "x86_64" else arch

    if (os0.contains("linux"))
      Some(s"$arch0-pc-linux")
    else if (os0.contains("mac"))
      Some(s"$arch0-apple-darwin")
    else if (os0.contains("windows"))
      Some(s"$arch0-pc-win32")
    else
      None
  }

  def get(): Option[String] =
    for {
      os   <- Option(System.getProperty("os.name"))
      arch <- Option(System.getProperty("os.arch"))
      p    <- get(os, arch)
    } yield p

  lazy val isArmArchitecture: Boolean =
    sys.props.getOrElse("os.arch", "").toLowerCase(Locale.ROOT) == "aarch64"
}
