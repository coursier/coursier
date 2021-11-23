package coursier.install.error

final class NoPrebuiltBinaryAvailable(val candidateUrls: Seq[String])
    extends InstallDirException(
      if (candidateUrls.isEmpty)
        "No prebuilt binary available"
      else
        s"No prebuilt binary available at ${candidateUrls.mkString(", ")}"
    )
