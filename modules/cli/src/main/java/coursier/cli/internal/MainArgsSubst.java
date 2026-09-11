package coursier.cli.internal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@TargetClass(className = "coursier.cli.internal.MainArgs")
@Platforms(Platform.WINDOWS.class)
final class MainArgsSubst {

    @Substitute
    String[] get(String[] args) {
        return WindowsMainArgs.get(args);
    }

}
