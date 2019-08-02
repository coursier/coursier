package coursier.cli.launch

sealed abstract class LaunchException(message: String, cause: Throwable = null) extends Exception(message, cause)

object LaunchException {
  // Specify one with -M or --main.
  final class NoMainClassFound extends LaunchException("Cannot find default main class")
  final class MainClassNotFound(mainClass: String, cause: Throwable) extends LaunchException(s"Class $mainClass not found", cause)
  final class MainMethodNotFound(mainClass: Class[_], cause: Throwable) extends LaunchException(s"Main method not found in $mainClass", cause)

  // constraint to be lifted later
  final class ClasspathHierarchyUnsupportedWhenForking extends LaunchException("Cannot start an application with a hierarchical classpath by forking")
}
