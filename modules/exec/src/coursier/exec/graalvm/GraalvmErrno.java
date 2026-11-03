package coursier.exec.graalvm;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Access to errno from native images
 *
 * <p>This relies only on the public GraalVM API, as SVM classes such as
 * {@code com.oracle.svm.core.headers.LibC} or {@code com.oracle.svm.core.posix.headers.Errno}
 * live in packages of the image builder that aren't exported to user code, so that calling
 * them from an image throws {@link NoClassDefFoundError} at run time.
 */
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class GraalvmErrno {

  private GraalvmErrno() {}

  /** The value of errno for the current thread */
  static int errno() {
    if (Platform.includedIn(Platform.LINUX.class))
      return LinuxFunctions.__errno_location().read();
    else
      return DarwinFunctions.__error().read();
  }

  /** The description of an errno value, or null if there's none */
  static String strerror(int errno) {
    return CTypeConversion.toJavaString(Functions.strerror(errno));
  }

  @CContext(PosixDirectives.class)
  @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
  static final class Functions {
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    static native CCharPointer strerror(int errnum);
  }

  @CContext(PosixDirectives.class)
  @Platforms(Platform.LINUX.class)
  static final class LinuxFunctions {
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    static native CIntPointer __errno_location();
  }

  @CContext(PosixDirectives.class)
  @Platforms(Platform.DARWIN.class)
  static final class DarwinFunctions {
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    static native CIntPointer __error();
  }

}
