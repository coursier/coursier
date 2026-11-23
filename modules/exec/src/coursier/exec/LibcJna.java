package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * The JVM implementations of the {@link Libc} methods, calling the C library via JNA.
 *
 * <p>Kept apart from {@link Libc}, whose methods are substituted in native images: GraalVM loads
 * {@link Libc} to apply its substitutions, which requires the types of its fields and method
 * signatures to be loadable. Nothing in {@link Libc} mentions JNA types, so that native images
 * can be built without JNA on their class path. Nothing should reference this class from native
 * images.
 */
final class LibcJna {

  private LibcJna() {}

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

  /**
   * The C library functions
   *
   * @throws LinkageError if the C library or JNA can't be loaded
   */
  static Functions functions() {
    return Holder.INSTANCE;
  }

  static void load() {
    functions();
  }

  static String strerror(int errno) {
    return functions().strerror(errno);
  }

  static int execve(String path, String[] argv, String[] envp) {
    functions().execve(path, argv, envp);
    return Native.getLastError();
  }

  static int chdir(String path) {
    return functions().chdir(path) == 0 ? 0 : Native.getLastError();
  }

  static int getFdFlags(int fd) {
    int ret = functions().fcntl(fd, Libc.F_GETFD, 0);
    return ret < 0 ? -Native.getLastError() : ret;
  }

  static int setFdFlags(int fd, int flags) {
    int ret = functions().fcntl(fd, Libc.F_SETFD, flags);
    return ret < 0 ? -Native.getLastError() : 0;
  }

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
        (short) (Libc.POSIX_SPAWN_SETEXEC | Libc.POSIX_SPAWN_CLOEXEC_DEFAULT)
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

}
