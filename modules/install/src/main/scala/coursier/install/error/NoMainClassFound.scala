package coursier.install.error

final class NoMainClassFound(val appName: String) extends InstallDirException(
      "No main class found" + (if (appName.isEmpty) "" else s" for $appName")
    ) {
  def this() =
    this("")
}
