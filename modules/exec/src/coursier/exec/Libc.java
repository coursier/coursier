package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

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
  static final int EBADF   = 9;

  // fcntl commands and flags, same values on Linux and macOS
  static final int F_GETFD    = 1;
  static final int F_SETFD    = 2;
  static final int FD_CLOEXEC = 1;

  // posix_spawn flags, from macOS' spawn.h
  static final short POSIX_SPAWN_SETEXEC         = 0x0040;
  static final short POSIX_SPAWN_CLOEXEC_DEFAULT = 0x4000;

  interface Functions extends Library {
    int execve(String filename, String[] argv, String[] envp);
    int chdir(String path);
    String strerror(int errnum);

    // fcntl is variadic. Declaring it with a fixed third argument is fine on Linux, where
    // variadic arguments are passed like the other ones, but not on macOS ARM64, where they
    // go on the stack. On macOS, only call it with commands that ignore that argument
    // (F_GETFD).
    int fcntl(int fd, int cmd, int arg);

    // macOS only. posix_spawnattr_t and posix_spawn_file_actions_t are pointers there.
    int posix_spawnattr_init(PointerByReference attr);
    int posix_spawnattr_setflags(PointerByReference attr, short flags);
    int posix_spawnattr_destroy(PointerByReference attr);
    int posix_spawn_file_actions_init(PointerByReference fileActions);
    int posix_spawn_file_actions_addinherit_np(PointerByReference fileActions, int fd);
    int posix_spawn_file_actions_destroy(PointerByReference fileActions);
    int posix_spawn(
      Pointer pid,
      String path,
      PointerByReference fileActions,
      PointerByReference attr,
      String[] argv,
      String[] envp
    );
  }

  private static final class Holder {
    static final Functions INSTANCE = Native.load("c", Functions.class);
  }

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

  // The methods below are substituted in native images (see coursier.exec.graalvm.LibcGraalvm),
  // and return errno values rather than relying on Native.getLastError() in their callers

  /**
   * Loads the C library
   *
   * @throws LinkageError if the C library or JNA can't be loaded
   */
  static void load() {
    functions();
  }

  /** Calls execve, and returns the resulting errno value, as execve only returns on error */
  static int execve(String path, String[] argv, String[] envp) {
    functions().execve(path, argv, envp);
    return Native.getLastError();
  }

  /** The descriptor flags of {@code fd} (F_GETFD), or a negative errno value */
  static int getFdFlags(int fd) {
    int ret = functions().fcntl(fd, F_GETFD, 0);
    return ret < 0 ? -Native.getLastError() : ret;
  }

  /**
   * Sets the descriptor flags of {@code fd} (F_SETFD), returns 0 or a negative errno value
   *
   * <p>Linux only, see {@link Functions#fcntl(int, int, int)}.
   */
  static int setFdFlags(int fd, int flags) {
    int ret = functions().fcntl(fd, F_SETFD, flags);
    return ret < 0 ? -Native.getLastError() : 0;
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
    Functions libc = functions();
    PointerByReference attr        = new PointerByReference();
    PointerByReference fileActions = new PointerByReference();
    int ret = libc.posix_spawnattr_init(attr);
    if (ret != 0)
      return ret;
    try {
      ret = libc.posix_spawnattr_setflags(
        attr,
        (short) (POSIX_SPAWN_SETEXEC | POSIX_SPAWN_CLOEXEC_DEFAULT)
      );
      if (ret != 0)
        return ret;
      ret = libc.posix_spawn_file_actions_init(fileActions);
      if (ret != 0)
        return ret;
      try {
        for (int fd : inheritedFds) {
          ret = libc.posix_spawn_file_actions_addinherit_np(fileActions, fd);
          if (ret != 0)
            return ret;
        }
        return libc.posix_spawn(null, path, fileActions, attr, argv, envp);
      } finally {
        libc.posix_spawn_file_actions_destroy(fileActions);
      }
    } finally {
      libc.posix_spawnattr_destroy(attr);
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
