package coursier.exec.graalvm;

import java.io.FileNotFoundException;

import coursier.exec.ErrnoException;
import coursier.exec.Execve;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.RuntimeSupport;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.exec.Execve")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class ExecveGraalvm {

  @Substitute
  public static boolean available() {
    return true;
  }

  // Arguments are checked by Execve.execve, that calls this
  @Substitute
  private static void execve0(String path, String[] command, String[] env) throws ErrnoException {
    ExecveGraalvmHelper.runShutdownHooks();

    CTypeConversion.CCharPointerHolder path0 = CTypeConversion.toCString(path);
    CTypeConversion.CCharPointerPointerHolder command0 = CTypeConversion.toCStrings(command);
    CTypeConversion.CCharPointerPointerHolder env0 = CTypeConversion.toCStrings(env);
    GraalvmUnistdExtras.execve(path0.get(), command0.get(), env0.get());

    int n = GraalvmErrno.errno();
    Throwable cause = null;
    if (n == GraalvmErrnoExtras.ENOENT() || n == GraalvmErrnoExtras.ENOTDIR())
      cause = new FileNotFoundException(path);
    throw new ErrnoException("execve " + path, n, cause);
  }

}

final class ExecveGraalvmHelper {

  static void runShutdownHooks() {
    try {
      RuntimeSupport.getRuntimeSupport().shutdown();
    } catch (Exception | LinkageError e) {
      // LinkageError: com.oracle.svm.core.jdk is an image builder package, only accessible
      // from images built with --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED
      Execve.runShutdownHooks();
    }
  }

}
