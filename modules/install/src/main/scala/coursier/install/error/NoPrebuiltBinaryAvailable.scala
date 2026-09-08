package coursier.install.error

final class NoPrebuiltBinaryAvailable(val appName: String, val candidateUrls: Seq[String])
    extends InstallDirException(
      s"No prebuilt binary available" +
        (if (appName.isEmpty) "" else s" for $appName") +
        (if (candidateUrls.isEmpty) "" else s" at ${candidateUrls.mkString(", ")}")
    ) {

  def this(candidateUrls: Seq[String]) =
    this("", candidateUrls)
}
