package coursier.cli.resolve

final class ResolveException(val message: String, cause: Throwable = null) extends Exception(message, cause)
