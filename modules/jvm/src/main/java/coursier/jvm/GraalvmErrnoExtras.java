package coursier.jvm;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

@CContext(PosixDirectives.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class GraalvmErrnoExtras {

    /** No such file or directory. */
    @CConstant
    public static native int ENOENT();

    /** Not a directory. */
    @CConstant
    public static native int ENOTDIR();

}
