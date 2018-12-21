package coursier.bootstrap

final class BootstrapException(val message: String, cause: Throwable = null) extends Exception(message, cause)

