package coursier.error

// should be effectively sealed (only ResolutionError and FetchError as direct implementations)
abstract class CoursierError(message: String, cause: Throwable = null) extends Exception(message, cause)
