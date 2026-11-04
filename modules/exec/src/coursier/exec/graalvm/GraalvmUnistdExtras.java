package coursier.exec.graalvm;

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

    @CFunction
    public static native int chdir(CCharPointer path);

    // from sys/stat.h rather than unistd.h, mode_t being an int or narrower
    @CFunction
    public static native int umask(int mask);

    // fcntl is variadic: this declaration is only fine on Linux, or for commands ignoring
    // their third argument (see coursier.exec.Libc.Functions#fcntl)
    @CFunction
    static native int fcntl(int fd, int cmd, int arg);

    // same values on Linux and macOS
    static final int F_GETFD = 1;
    static final int F_SETFD = 2;

}
