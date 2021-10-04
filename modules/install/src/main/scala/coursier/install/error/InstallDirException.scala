package coursier.install.error

abstract class InstallDirException(message: String, cause: Throwable = null)
    extends Exception(message, cause)
