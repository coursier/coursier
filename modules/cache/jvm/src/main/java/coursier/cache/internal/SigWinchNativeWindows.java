package coursier.cache.internal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.cache.internal.SigWinch")
@Platforms(Platform.WINDOWS.class)
public final class SigWinchNativeWindows {

  @Substitute
  public static void addHandler(Runnable runnable) {
    // terminal size changes ignored for now
  }

}
