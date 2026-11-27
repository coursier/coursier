package coursier.paths

import java.nio.file.{FileAlreadyExistsException, Files, Path}

/** Scala Native counterpart of the `coursier.paths.Util` Java class of the `paths` module.
  *
  * Only the subset the cache module needs is implemented. `paths` is a Java module, and Scala
  * Native cannot compile Java sources, hence this re-implementation.
  */
object Util {

  def createDirectories(path: Path): Unit =
    try Files.createDirectories(path)
    catch {
      case ex: FileAlreadyExistsException =>
        // see https://bugs.openjdk.java.net/browse/JDK-8130464
        if (!Files.isDirectory(path))
          throw ex
    }

  /** Deletes the file at `path` if it is a symbolic link.
    *
    * Meant to be called before opening a file of the cache that coursier writes to (temporary
    * files, lock files, …), so that a symbolic link planted there by someone else having write
    * access to the cache is removed (deleting a link leaves its target alone) rather than followed.
    *
    * On Scala Native, this is the only protection: its `FileChannel.open` ignores
    * `LinkOption.NOFOLLOW_LINKS`, and follows links.
    */
  def deleteIfSymbolicLink(path: Path): Unit =
    if (Files.isSymbolicLink(path))
      Files.deleteIfExists(path)

  private def envEnabled(value: String): Option[Boolean] =
    value.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "enable" | "1"   => Some(true)
      case "false" | "disable" | "0" => Some(false)
      case _                         => None
    }

  private lazy val useColorOutput0: Boolean =
    if (System.getenv("INSIDE_EMACS") != null) false
    else
      Option(System.getenv("COURSIER_PROGRESS")).flatMap(envEnabled)
        .getOrElse(System.getenv("COURSIER_NO_TERM") == null)

  def useColorOutput(): Boolean = useColorOutput0

  private lazy val useAnsiOutput0: Boolean =
    Terminal.isTerminal &&
    System.getenv("INSIDE_EMACS") == null &&
    System.getenv("CI") == null &&
    Option(System.getenv("COURSIER_PROGRESS")).flatMap(envEnabled)
      .getOrElse(System.getenv("COURSIER_NO_TERM") == null)

  def useAnsiOutput(): Boolean = useAnsiOutput0

  /** Always false - the JNI bits this guards are Windows-only, and coursier's Windows JNI helpers
    * are a JVM library.
    */
  def useJni(): Boolean = false

  def useJni(beforeJni: Runnable): Boolean = false

  private object Terminal {
    import scala.scalanative.posix.unistd
    def isTerminal: Boolean = unistd.isatty(1) == 1
  }
}
