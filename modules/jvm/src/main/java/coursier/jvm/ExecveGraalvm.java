package coursier.jvm;

import java.io.FileNotFoundException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.Unistd;
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
    Unistd.execve(path0.get(), command0.get(), env0.get());

    int n = Errno.errno();
    Throwable cause = null;
    if (n == Errno.ENOENT() || n == Errno.ENOTDIR())
      cause = new FileNotFoundException(path);
    throw new ErrnoException(n, cause);
  }

}
