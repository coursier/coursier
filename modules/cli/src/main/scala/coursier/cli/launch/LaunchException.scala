package coursier.cli.launch

import java.lang.reflect.Method

sealed abstract class LaunchException(message: String, cause: Throwable = null) extends Exception(message, cause)

object LaunchException {
  // Specify one with -M or --main.
  final class NoMainClassFound extends LaunchException("Cannot find default main class")
  final class MainClassNotFound(mainClass: String, cause: Throwable) extends LaunchException(s"Class $mainClass not found", cause)
  final class MainMethodNotFound(mainClass: Class[_], cause: Throwable) extends LaunchException(s"Main method not found in $mainClass", cause)
  final class NonStaticMainMethod(mainClass: Class[_], method: Method) extends LaunchException(s"Main method in $mainClass is not static")

  // constraint to be lifted later
  final class ClasspathHierarchyUnsupportedWhenForking extends LaunchException("Cannot start an application with a hierarchical classpath by forking")
}
