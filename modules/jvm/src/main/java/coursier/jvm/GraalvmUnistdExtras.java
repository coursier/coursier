package coursier.jvm;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

@CContext(PosixDirectives.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class GraalvmUnistdExtras {

    @CFunction
    public static native int execve(CCharPointer path, CCharPointerPointer argv, CCharPointerPointer envp);

}
