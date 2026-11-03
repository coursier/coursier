package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * The C library functions used by this module on the JVM, called via JNA.
 *
 * <p>In native images, the methods here that call into the C library are replaced by GraalVM
 * substitutions (see {@code coursier.exec.graalvm}), so that JNA isn't needed there.
 */
final class Libc {

  private Libc() {}

  // Same values on Linux and macOS
  static final int ENOENT  = 2;
  static final int ENOTDIR = 20;

  interface Functions extends Library {
    int execve(String filename, String[] argv, String[] envp);
    int chdir(String path);
    String strerror(int errnum);
  }

  private static final class Holder {
    static final Functions INSTANCE = Native.load("c", Functions.class);
  }

  /** Whether the current OS is one this module supports (Linux or macOS) */
  static boolean isSupportedOs() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return osName.contains("linux") || osName.contains("mac");
  }

  /** Whether the current OS is supported, and the C library can be loaded via JNA */
  static boolean available() {
    if (!isSupportedOs())
      return false;
    try {
      return Holder.INSTANCE != null;
    } catch (LinkageError e) {
      return false;
    }
  }

  /**
   * The C library functions
   *
   * @throws LinkageError if the C library or JNA can't be loaded
   */
  static Functions functions() {
    return Holder.INSTANCE;
  }

  /** The description of an errno value, or null if it can't be computed */
  static String strerror(int errno) {
    try {
      return functions().strerror(errno);
    } catch (LinkageError e) {
      return null;
    }
  }

  /**
   * Creates an exception for a failed system call that took {@code path} as argument, having
   * a {@link FileNotFoundException} as cause if {@code path} doesn't exist
   */
  static ErrnoException exception(String operation, String path, int errno) {
    Throwable cause = null;
    if (errno == ENOENT || errno == ENOTDIR)
      cause = new FileNotFoundException(path);
    return new ErrnoException(operation, errno, cause);
  }

}
