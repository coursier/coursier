package coursier.exec.graalvm;

import coursier.exec.Execve;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.RuntimeSupport;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.exec.Execve")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class ExecveGraalvm {

  @Substitute
  public static boolean available() {
    return true;
  }

  @Substitute
  private static void runShutdownHooks0() {
    ExecveGraalvmHelper.runShutdownHooks();
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
