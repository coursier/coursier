package coursier.install.error

final class InvalidAppName(val name: String, val reason: String)
    extends InstallDirException(s"Invalid app name '$name': $reason")
