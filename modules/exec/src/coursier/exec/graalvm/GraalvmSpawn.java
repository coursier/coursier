package coursier.exec.graalvm;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

/**
 * posix_spawn functions of macOS, where posix_spawnattr_t and posix_spawn_file_actions_t are
 * pointers
 */
@CContext(PosixDirectives.class)
@Platforms(Platform.DARWIN.class)
final class GraalvmSpawn {

  private GraalvmSpawn() {}

  // from spawn.h
  static final short POSIX_SPAWN_SETEXEC         = 0x0040;
  static final short POSIX_SPAWN_CLOEXEC_DEFAULT = 0x4000;

  @CFunction
  static native int posix_spawnattr_init(WordPointer attr);

  @CFunction
  static native int posix_spawnattr_setflags(WordPointer attr, short flags);

  @CFunction
  static native int posix_spawnattr_destroy(WordPointer attr);

  @CFunction
  static native int posix_spawn_file_actions_init(WordPointer fileActions);

  @CFunction
  static native int posix_spawn_file_actions_addinherit_np(WordPointer fileActions, int fd);

  @CFunction
  static native int posix_spawn_file_actions_destroy(WordPointer fileActions);

  @CFunction
  static native int posix_spawn(
    CIntPointer pid,
    CCharPointer path,
    WordPointer fileActions,
    WordPointer attr,
    CCharPointerPointer argv,
    CCharPointerPointer envp
  );

}
