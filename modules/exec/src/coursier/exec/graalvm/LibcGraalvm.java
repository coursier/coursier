package coursier.exec.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.exec.Libc")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class LibcGraalvm {

  @Substitute
  static String strerror(int errno) {
    return GraalvmErrno.strerror(errno);
  }

}
