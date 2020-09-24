package coursier.jvm;

import java.io.FileNotFoundException;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.jvm.Execve")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class ExecveGraalvm {

  @Substitute
  public static boolean available() {
    return true;
  }

  @Substitute
  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    CTypeConversion.CCharPointerHolder path0 = CTypeConversion.toCString(path);
    CTypeConversion.CCharPointerPointerHolder command0 = CTypeConversion.toCStrings(command);
    CTypeConversion.CCharPointerPointerHolder env0 = CTypeConversion.toCStrings(env);
    GraalvmUnistdExtras.execve(path0.get(), command0.get(), env0.get());

    int n = CErrorNumber.getCErrorNumber();
    Throwable cause = null;
    if (n == GraalvmErrnoExtras.ENOENT() || n == GraalvmErrnoExtras.ENOTDIR())
      cause = new FileNotFoundException(path);
    throw new ErrnoException(n, cause);
  }

}
