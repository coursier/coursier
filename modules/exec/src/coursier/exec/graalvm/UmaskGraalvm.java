package coursier.exec.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.exec.Umask")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class UmaskGraalvm {

  @Substitute
  public static boolean available() {
    return true;
  }

  @Substitute
  public static int umask(int mask) {
    // mode_t is only 16 bits wide on macOS
    return GraalvmUnistdExtras.umask(mask) & 0777;
  }

}
