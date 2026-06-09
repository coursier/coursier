// based on https://github.com/VirtusLab/scala-cli/blob/e782348c196b99ed028de11867149f4d400ec083/modules/build/src/main/java/scala/build/internal/ChdirGraalvm.java
package coursier.exec;

import java.io.FileNotFoundException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.headers.LibC;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.exec.Chdir")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class ChdirGraalvm {

  @Substitute
  public static boolean available() {
    return true;
  }

  @Substitute
  public static void chdir(String path) throws ErrnoException {
    CTypeConversion.CCharPointerHolder path0 = CTypeConversion.toCString(path);
    int ret = GraalvmUnistdExtras.chdir(path0.get());

    if (ret != 0) {
      int n = LibC.errno();
      Throwable cause = null;
      if (n == GraalvmErrnoExtras.ENOENT() || n == GraalvmErrnoExtras.ENOTDIR())
        cause = new FileNotFoundException(path);
      throw new ErrnoException(n, cause);
    }
  }

}
