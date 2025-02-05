package coursier.error

abstract class DependencyError(val message: String, cause: Throwable = null)
    extends Exception(message, cause)
