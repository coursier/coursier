package coursier.exec;

import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * The C library functions used by this module
 *
 * <p>On the JVM, these call the C library via JNA (see {@link LibcJna}). In native images, the
 * methods here that call into the C library are replaced by GraalVM substitutions (see
 * {@code coursier.exec.graalvm}), so that JNA isn't needed there. Applying them requires GraalVM
 * to load this class, so no JNA type should appear in its fields or method signatures: JNA
 * might not be on the class path of native images.
 */
final class Libc {

  private Libc() {}

  // Same values on Linux and macOS
  static final int ENOENT  = 2;
  static final int ENOTDIR = 20;
  static final int EBADF   = 9;

  // fcntl commands and flags, same values on Linux and macOS
  static final int F_GETFD    = 1;
  static final int F_SETFD    = 2;
  static final int FD_CLOEXEC = 1;

  // posix_spawn flags, from macOS' spawn.h
  static final short POSIX_SPAWN_SETEXEC         = 0x0040;
  static final short POSIX_SPAWN_CLOEXEC_DEFAULT = 0x4000;

  /** Whether the current OS is one this module supports (Linux or macOS) */
  static boolean isSupportedOs() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return osName.contains("linux") || osName.contains("mac");
  }

  /** Whether the current OS is macOS */
  static boolean isMacOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
  }

  /** Whether the current OS is supported, and the C library can be loaded via JNA */
  static boolean available() {
    if (!isSupportedOs())
      return false;
    try {
      LibcJna.load();
      return true;
    } catch (LinkageError e) {
      return false;
    }
  }

  /** The description of an errno value, or null if it can't be computed */
  static String strerror(int errno) {
    try {
      return LibcJna.strerror(errno);
    } catch (LinkageError e) {
      return null;
    }
  }

  /**
   * Calls chdir, and returns 0 on success, or the resulting errno value
   *
   * <p>Not substituted, but only called from {@code Chdir.chdir0}, which is.
   */
  static int chdir(String path) {
    return LibcJna.chdir(path);
  }

  // The methods below are substituted in native images (see coursier.exec.graalvm.LibcGraalvm),
  // and return errno values rather than relying on JNA's Native.getLastError() in their callers

  /**
   * Loads the C library
   *
   * @throws LinkageError if the C library or JNA can't be loaded
   */
  static void load() {
    LibcJna.load();
  }

  /** Calls execve, and returns the resulting errno value, as execve only returns on error */
  static int execve(String path, String[] argv, String[] envp) {
    return LibcJna.execve(path, argv, envp);
  }

  /** The descriptor flags of {@code fd} (F_GETFD), or a negative errno value */
  static int getFdFlags(int fd) {
    return LibcJna.getFdFlags(fd);
  }

  /**
   * Sets the descriptor flags of {@code fd} (F_SETFD), returns 0 or a negative errno value
   *
   * <p>Linux only, see {@link LibcJna.Functions#fcntl(int, int, int)}.
   */
  static int setFdFlags(int fd, int flags) {
    return LibcJna.setFdFlags(fd, flags);
  }

  /**
   * Replaces the current process by another program with posix_spawn and
   * POSIX_SPAWN_SETEXEC, only keeping the descriptors in {@code inheritedFds} open
   * (POSIX_SPAWN_CLOEXEC_DEFAULT), and returns the resulting errno value, as this only returns
   * on error
   *
   * <p>macOS only.
   */
  static int spawnExec(String path, String[] argv, String[] envp, int[] inheritedFds) {
    return LibcJna.spawnExec(path, argv, envp, inheritedFds);
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
