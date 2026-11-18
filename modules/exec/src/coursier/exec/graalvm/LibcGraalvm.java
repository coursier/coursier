package coursier.exec.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;

@TargetClass(className = "coursier.exec.Libc")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class LibcGraalvm {

  @Substitute
  static String strerror(int errno) {
    return GraalvmErrno.strerror(errno);
  }

  // Nothing to load, native images call the C library directly
  @Substitute
  static void load() {}

  @Substitute
  static int execve(String path, String[] argv, String[] envp) {
    CTypeConversion.CCharPointerHolder path0 = CTypeConversion.toCString(path);
    CTypeConversion.CCharPointerPointerHolder argv0 = CTypeConversion.toCStrings(argv);
    CTypeConversion.CCharPointerPointerHolder envp0 = CTypeConversion.toCStrings(envp);
    GraalvmUnistdExtras.execve(path0.get(), argv0.get(), envp0.get());
    return GraalvmErrno.errno();
  }

  @Substitute
  static int getFdFlags(int fd) {
    int ret = GraalvmUnistdExtras.fcntl(fd, GraalvmUnistdExtras.F_GETFD, 0);
    return ret < 0 ? -GraalvmErrno.errno() : ret;
  }

  @Substitute
  static int setFdFlags(int fd, int flags) {
    int ret = GraalvmUnistdExtras.fcntl(fd, GraalvmUnistdExtras.F_SETFD, flags);
    return ret < 0 ? -GraalvmErrno.errno() : 0;
  }

  @Substitute
  static int spawnExec(String path, String[] argv, String[] envp, int[] inheritedFds) {
    if (!Platform.includedIn(Platform.DARWIN.class))
      throw new UnsupportedOperationException("posix_spawn is only used on macOS");

    WordPointer attr        = StackValue.get(WordPointer.class);
    WordPointer fileActions = StackValue.get(WordPointer.class);
    CIntPointer pid         = StackValue.get(CIntPointer.class);
    int ret = GraalvmSpawn.posix_spawnattr_init(attr);
    if (ret != 0)
      return ret;
    try {
      ret = GraalvmSpawn.posix_spawnattr_setflags(
        attr,
        (short) (GraalvmSpawn.POSIX_SPAWN_SETEXEC | GraalvmSpawn.POSIX_SPAWN_CLOEXEC_DEFAULT)
      );
      if (ret != 0)
        return ret;
      ret = GraalvmSpawn.posix_spawn_file_actions_init(fileActions);
      if (ret != 0)
        return ret;
      try {
        for (int fd : inheritedFds) {
          ret = GraalvmSpawn.posix_spawn_file_actions_addinherit_np(fileActions, fd);
          if (ret != 0)
            return ret;
        }
        CTypeConversion.CCharPointerHolder path0 = CTypeConversion.toCString(path);
        CTypeConversion.CCharPointerPointerHolder argv0 = CTypeConversion.toCStrings(argv);
        CTypeConversion.CCharPointerPointerHolder envp0 = CTypeConversion.toCStrings(envp);
        return GraalvmSpawn.posix_spawn(
          pid,
          path0.get(),
          fileActions,
          attr,
          argv0.get(),
          envp0.get()
        );
      } finally {
        GraalvmSpawn.posix_spawn_file_actions_destroy(fileActions);
      }
    } finally {
      GraalvmSpawn.posix_spawnattr_destroy(attr);
    }
  }

}
