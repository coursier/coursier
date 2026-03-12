package coursier.cli.internal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import java.nio.file.Path;

@TargetClass(className = "coursier.cli.internal.Argv0")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Argv0Subst {

    @Substitute
    String get(String defaultValue) {
        return ProcessProperties.getArgumentVectorProgramName();
    }

}
